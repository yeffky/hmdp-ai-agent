package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.state.ReActAgentState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 上下文管理节点 — 图入口，注入结构化上下文块并执行滑动窗口/压缩。
 *
 * <p>构建的 contextBlock 供 Planner/Executor/Observer/Answer 共用，包含：
 * <ol>
 *   <li>System Prompt（核心规则）</li>
 *   <li>用户画像（跨会话持久化记忆，含时间戳冲突解决）</li>
 *   <li>记忆摘要（压缩后的历史摘要）</li>
 *   <li>对话历史（最近消息）</li>
 * </ol>
 * <p>工具调用结果不在此块中，由各节点从 state.scratchpad() 动态读取。</p>
 */
public class ContextNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ContextNode.class);

    private final SlidingWindowManager windowManager;
    private final UserStore userStore;

    private static final String SYSTEM_RULES = """
            ## 核心规则
            - 绝对不要为了迎合用户而强行编造逻辑来解释冲突。
            - 如果发现不可调和的事实矛盾，请直接询问用户，而不是自作主张地修改历史数据。
            - 当用户提供的信息前后冲突时，优先采纳用户近期的表述。
            - 你不会的就坦诚告知，不要编造。
            """;

    public ContextNode(SlidingWindowManager windowManager, UserStore userStore) {
        this.windowManager = windowManager;
        this.userStore = userStore;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        log.debug("ContextNode: building context for session {}", state.sessionId());

        // 1. 滑动窗口压缩
        Map<String, Object> updates = new LinkedHashMap<>(windowManager.manageContext(state));

        // 2. 构建结构化上下文块
        String contextBlock = buildContextBlock(state);
        updates.put("contextBlock", contextBlock);

        // 3. 确保路由到 planner
        if (!updates.containsKey("nextNode")) {
            updates.put("nextNode", "planner");
        }
        return updates;
    }

    private String buildContextBlock(ReActAgentState state) {
        StringBuilder sb = new StringBuilder();

        // --- System Prompt（规则） ---
        sb.append(SYSTEM_RULES);

        // --- 用户画像（从 PostgreSQL 读取） ---
        Long userId = extractUserId(state);
        if (userId != null) {
            String profile = userStore.toPromptContext(userId);
            if (!profile.isEmpty()) {
                sb.append("\n").append(profile).append("\n");
            }
        }

        // --- 记忆摘要 ---
        String summary = state.compressedSummary();
        if (summary != null && !summary.isEmpty()) {
            sb.append("\n## 历史记忆\n").append(summary).append("\n");
        }

        // --- 对话历史（最近消息，从 state.messages 格式化） ---
        List<Map<String, String>> messages = state.messages();
        if (messages != null && !messages.isEmpty()) {
            sb.append("\n## 对话历史\n");
            // 只取最后 20 条，避免过长
            int start = Math.max(0, messages.size() - 20);
            for (int i = start; i < messages.size(); i++) {
                Map<String, String> m = messages.get(i);
                String role = m.getOrDefault("role", "?");
                String content = m.getOrDefault("content", "");
                if (content.length() > 300) content = content.substring(0, 300) + "...";
                sb.append(role).append(": ").append(content).append("\n");
            }
        }

        return sb.toString();
    }

    private Long extractUserId(ReActAgentState state) {
        try {
            Object val = state.data().get("userId");
            if (val instanceof Number) return ((Number) val).longValue();
            if (val instanceof String) return Long.parseLong((String) val);
        } catch (Exception ignored) {}
        return null;
    }
}
