package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.dto.ChatHistoryRound;
import com.hmdp.repository.ChatHistoryRepository;
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
 *   <li>对话历史（从 PostgreSQL tb_chat_history 加载最近 N 轮）</li>
 * </ol>
 * <p>工具调用结果不在此块中，由各节点从 state.scratchpad() 动态读取。</p>
 */
public class ContextNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ContextNode.class);

    private final SlidingWindowManager windowManager;
    private final UserStore userStore;
    private final ChatHistoryRepository chatHistoryRepo;

    private static final int MAX_CONTEXT_ROUNDS = 3;

    private static final String SYSTEM_RULES = """
            ## 核心规则
            - 绝对不要为了迎合用户而强行编造逻辑来解释冲突。
            - 如果发现不可调和的事实矛盾，请直接询问用户，而不是自作主张地修改历史数据。
            - 当用户提供的信息前后冲突时，优先采纳用户近期的表述。
            - 你不会的就坦诚告知，不要编造。
            - 历史对话仅作上下文参考。用户当前问题如果不再要求「附近」「周边」，就不要因为历史缺经纬度而继续索要地理位置。
            - 用户改变主意时，无条件跟随新意图，不要被历史需求绑架。
            """;

    public ContextNode(SlidingWindowManager windowManager, UserStore userStore,
                       ChatHistoryRepository chatHistoryRepo) {
        this.windowManager = windowManager;
        this.userStore = userStore;
        this.chatHistoryRepo = chatHistoryRepo;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        long t0 = System.currentTimeMillis();
        log.debug("ContextNode: building context for session {}", state.sessionId());

        // 1. 滑动窗口压缩
        Map<String, Object> updates = new LinkedHashMap<>(windowManager.manageContext(state));
        log.info("ContextNode: sliding window done in {} ms, messages={}",
                System.currentTimeMillis() - t0, state.messages().size());

        // 2. 构建结构化上下文块
        long t1 = System.currentTimeMillis();
        String contextBlock = buildContextBlock(state);
        updates.put("contextBlock", contextBlock);
        log.info("ContextNode: context block built in {} ms ({} chars)",
                System.currentTimeMillis() - t1, contextBlock.length());

        // 3. 确保路由到 planner
        if (!updates.containsKey("nextNode")) {
            updates.put("nextNode", "planner");
        }
        log.info("ContextNode: total {} ms", System.currentTimeMillis() - t0);
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

        // --- 对话历史（优先从 checkpoint state.messages() 读取，不足时用 MySQL 补充） ---
        List<Map<String, String>> stateMsgs = state.messages();
        if (!stateMsgs.isEmpty()) {
            sb.append("\n## 对话历史\n");
            int maxPairs = Math.min(stateMsgs.size() / 2, MAX_CONTEXT_ROUNDS);
            int start = Math.max(0, stateMsgs.size() - maxPairs * 2);
            for (int i = start; i < stateMsgs.size(); i++) {
                Map<String, String> m = stateMsgs.get(i);
                String role = m.get("role");
                String content = m.get("content");
                if (content != null && content.length() > 300)
                    content = content.substring(0, 300) + "...";
                if ("user".equals(role)) {
                    sb.append("user: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    sb.append("assistant: ").append(content).append("\n");
                }
            }
        } else if (userId != null && chatHistoryRepo != null) {
            // checkpoint 无消息时回退到 MySQL（迁移期兼容）
            List<ChatHistoryRound> rounds = chatHistoryRepo.findRounds(userId, null, MAX_CONTEXT_ROUNDS);
            if (!rounds.isEmpty()) {
                sb.append("\n## 对话历史\n");
                for (int i = rounds.size() - 1; i >= 0; i--) {
                    ChatHistoryRound r = rounds.get(i);
                    String userMsg = r.getUserMessage();
                    String aiMsg = r.getAssistantMessage();
                    if (userMsg != null && userMsg.length() > 300)
                        userMsg = userMsg.substring(0, 300) + "...";
                    if (aiMsg != null && aiMsg.length() > 300)
                        aiMsg = aiMsg.substring(0, 300) + "...";
                    sb.append("user: ").append(userMsg).append("\n");
                    sb.append("assistant: ").append(aiMsg).append("\n");
                }
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
