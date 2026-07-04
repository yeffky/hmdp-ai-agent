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

    private static final int MAX_CONTEXT_ROUNDS = 10;

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

        // --- 对话历史（从 PostgreSQL tb_chat_history 加载最近 N 轮） ---
        if (userId != null && chatHistoryRepo != null) {
            List<ChatHistoryRound> rounds = chatHistoryRepo.findRounds(userId, null, MAX_CONTEXT_ROUNDS);
            if (!rounds.isEmpty()) {
                sb.append("\n## 对话历史\n");
                // findRounds returns DESC (newest first), reverse to chronological
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
