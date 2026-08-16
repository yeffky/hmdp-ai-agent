package com.hmdp.agent.graph.state;

import com.hmdp.agent.graph.NodeNames;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Reducer;
import org.bsc.langgraph4j.state.RemoveByHash;

import java.util.*;

/**
 * ReAct Agent 图状态 Schema — 基于 LangGraph4j 1.8.19 Delta Channels。
 *
 * <h3>Delta Channel 机制</h3>
 * <ul>
 *   <li><b>{@link Channels#appender}</b> — 消息增量追加通道。
 *       节点只返回新增的消息，框架自动追加到已有列表。
 *       支持 {@link RemoveByHash} 标记删除列表中特定项，无需传全量列表。</li>
 *   <li><b>{@link Channels#base}</b> — 标量字段通道（last-write-wins）。
 *       节点返回值覆盖旧值，通过 Reducer 控制合并语义。</li>
 * </ul>
 *
 * <h3>节点返回的 Delta 示例</h3>
 * <pre>{@code
 * // AnswerNode: 追加本轮 Q&A
 * return Map.of("messages", List.of(userMsg, aiMsg));  // 增量追加
 *
 * // SlidingWindowManager: 压缩后替换消息列表（全量操作）
 * return Map.of("messages", trimmedList);  // 全量替换
 *
 * // 删除特定消息（高级用法）
 * return Map.of("messages", List.of(RemoveByHash.of(targetMsg)));
 * }</pre>
 */
public final class StateSchema {

    private StateSchema() {}

    // ======== Delta Channel: 消息增量追加 ========
    //
    // 使用 Channels.appender()：
    //   - 节点返回 List.of(msg1, msg2) → 自动追加到已有消息列表
    //   - 节点返回 List.of(RemoveByHash.of(oldMsg)) → 从列表中移除匹配项
    //   - 节点返回完整列表 → 全量替换（用于压缩场景）
    //
    @SuppressWarnings("unchecked")
    public static final Channel<List<Map<String, String>>> MESSAGES =
            Channels.appender(ArrayList::new);

    // ======== 标量字段: last-write-wins ========

    @SuppressWarnings("unchecked")
    public static final Channel<Map<String, Object>> SCRATCHPAD = Channels.base(
            (Reducer<Map<String, Object>>) (oldVal, newVal) -> newVal,
            (java.util.function.Supplier<Map<String, Object>>) LinkedHashMap::new
    );

    @SuppressWarnings("unchecked")
    private static <T> Channel<T> lww(T defaultVal) {
        return Channels.base(
                (Reducer<T>) (oldVal, newVal) -> newVal,
                (java.util.function.Supplier<T>) () -> defaultVal
        );
    }

    public static final Channel<String> SESSION_ID          = lww("");
    public static final Channel<String> USER_QUERY          = lww("");
    public static final Channel<String> PLAN                 = lww("");
    public static final Channel<String> SELECTED_SKILLS      = lww("");
    public static final Channel<String> FINAL_ANSWER        = lww("");
    public static final Channel<String> NEXT_NODE           = lww(NodeNames.CONTEXT);
    public static final Channel<String> COMPRESSED_SUMMARY  = lww("");
    public static final Channel<String> ROUND_EVIDENCE      = lww("");
    public static final Channel<String> CONTEXT_BLOCK       = lww("");
    public static final Channel<String> CONTEXT_BLOCK_NO_HISTORY = lww("");
    public static final Channel<String> STREAMING_PROMPT    = lww("");
    public static final Channel<String>  ERROR_CATEGORY      = lww("");
    public static final Channel<String>  LAST_TOOL_NAME      = lww("");
    public static final Channel<String>  LAST_TOOL_ARGS      = lww("");
    public static final Channel<Long>    USER_ID              = lww(0L);
    public static final Channel<String>  USER_LOCATION        = lww("");
    public static final Channel<Long>    USER_DISTRICT_ID     = lww(0L);
    public static final Channel<String>  ERROR_CONTEXT        = lww("");
    public static final Channel<Boolean> PENDING_CONFIRMATION  = lww(false);
    public static final Channel<String>  CONFIRMATION_PROMPT   = lww("");
    public static final Channel<String>  USER_CHOICE           = lww("");
    public static final Channel<String>  PENDING_WRITE          = lww("");
    public static final Channel<String>  PENDING_OPTIONS        = lww("");

    /**
     * 轮次计数器（单一 Map channel，last-write-wins）—— 收敛 iteration/replanCount/
     * toolCallCount/emptyResultRetries 等散落标量，轮间由 ContextNode 统一重置。
     */
    @SuppressWarnings("unchecked")
    public static final Channel<Map<String, Object>> COUNTERS = Channels.base(
            (Reducer<Map<String, Object>>) (oldVal, newVal) -> newVal,
            (java.util.function.Supplier<Map<String, Object>>) LinkedHashMap::new
    );

    // ======== Channel Map ========

    public static Map<String, Channel<?>> channelMap() {
        Map<String, Channel<?>> channels = new LinkedHashMap<>();
        channels.put("messages",          MESSAGES);
        channels.put("scratchpad",        SCRATCHPAD);
        channels.put("sessionId",         SESSION_ID);
        channels.put("userQuery",         USER_QUERY);
        channels.put("plan",              PLAN);
        channels.put("selectedSkills",    SELECTED_SKILLS);
        channels.put("finalAnswer",       FINAL_ANSWER);
        channels.put("nextNode",          NEXT_NODE);
        channels.put("compressedSummary", COMPRESSED_SUMMARY);
        channels.put("roundEvidence",     ROUND_EVIDENCE);
        channels.put("contextBlock",      CONTEXT_BLOCK);
        channels.put("contextBlockNoHistory", CONTEXT_BLOCK_NO_HISTORY);
        channels.put("streamingPrompt",  STREAMING_PROMPT);
        channels.put("errorCategory",      ERROR_CATEGORY);
        channels.put("lastToolName",       LAST_TOOL_NAME);
        channels.put("lastToolArgs",       LAST_TOOL_ARGS);
        channels.put("userId",             USER_ID);
        channels.put("userLocation",       USER_LOCATION);
        channels.put("userDistrictId",     USER_DISTRICT_ID);
        channels.put("errorContext",       ERROR_CONTEXT);
        channels.put("pendingConfirmation", PENDING_CONFIRMATION);
        channels.put("confirmationPrompt",  CONFIRMATION_PROMPT);
        channels.put("userChoice",          USER_CHOICE);
        channels.put("pendingWrite",        PENDING_WRITE);
        channels.put("pendingOptions",      PENDING_OPTIONS);
        channels.put("counters",            COUNTERS);
        return channels;
    }
}
