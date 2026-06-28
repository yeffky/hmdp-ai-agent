package com.hmdp.agent.graph.state;

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
    public static final Channel<String> PLAN_JSON           = lww("");
    public static final Channel<String> FINAL_ANSWER        = lww("");
    public static final Channel<String> NEXT_NODE           = lww("context");
    public static final Channel<String> COMPRESSED_SUMMARY  = lww("");
    public static final Channel<String> OBSERVER_FEEDBACK   = lww("");
    public static final Channel<Integer> ITERATION          = lww(0);
    public static final Channel<Integer> TOOL_FAILURES       = lww(0);

    // ======== Channel Map ========

    public static Map<String, Channel<?>> channelMap() {
        Map<String, Channel<?>> channels = new LinkedHashMap<>();
        channels.put("messages",          MESSAGES);
        channels.put("scratchpad",        SCRATCHPAD);
        channels.put("sessionId",         SESSION_ID);
        channels.put("userQuery",         USER_QUERY);
        channels.put("planJson",          PLAN_JSON);
        channels.put("finalAnswer",       FINAL_ANSWER);
        channels.put("nextNode",          NEXT_NODE);
        channels.put("compressedSummary", COMPRESSED_SUMMARY);
        channels.put("observerFeedback",  OBSERVER_FEEDBACK);
        channels.put("iteration",         ITERATION);
        channels.put("toolFailures",       TOOL_FAILURES);
        return channels;
    }
}
