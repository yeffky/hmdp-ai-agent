package com.hmdp.agent.graph.state;

import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

/**
 * ReAct Agent 图状态 — 扩展 LangGraph4j 的 AgentState，
 * 提供类型安全的访问器，配合 StateSchema 中的 Channel 定义使用。
 */
public class ReActAgentState extends AgentState {

    /** LangGraph4j 要求的 Map 构造器（由 AgentStateFactory 调用） */
    public ReActAgentState(Map<String, Object> initData) {
        super(initData);
    }

    // ======== 类型安全的访问器 ========

    public String sessionId() {
        return this.<String>value("sessionId").orElse(null);
    }

    public String userQuery() {
        return this.<String>value("userQuery").orElse(null);
    }

    /** Planner 语义选中的技能名列表（JSON 数组字符串存储，转 List；空返回空列表） */
    public java.util.List<String> selectedSkills() {
        String raw = this.<String>value("selectedSkills").orElse("");
        if (raw == null || raw.isBlank()) return new java.util.ArrayList<>();
        try {
            return cn.hutool.json.JSONUtil.parseArray(raw).toList(String.class);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    public String plan() {
        return this.<String>value("plan").orElse("");
    }

    public String finalAnswer() {
        return this.<String>value("finalAnswer").orElse(null);
    }

    public String nextNode() {
        return this.<String>value(StateKeys.NEXT_NODE).orElse(null);
    }

    public String compressedSummary() {
        return this.<String>value("compressedSummary").orElse("");
    }

    public String roundEvidence() {
        return this.<String>value("roundEvidence").orElse(null);
    }

    public String contextBlock() {
        return this.<String>value("contextBlock").orElse("");
    }

    /** 不含对话历史段的上下文块（画像/定位/摘要/经验，供 Agent 决策与 Answer 注入——历史由 trimmed messages 承担） */
    public String contextBlockNoHistory() {
        return this.<String>value("contextBlockNoHistory").orElse("");
    }

    public String streamingPrompt() {
        return this.<String>value("streamingPrompt").orElse(null);
    }

    public int iteration() {
        return counter(StateKeys.ITERATION);
    }

    public int retryCount() {
        return counter(StateKeys.RETRY_COUNT);
    }

    public String errorCategory() {
        return this.<String>value("errorCategory").orElse("");
    }

    public String lastToolName() {
        return this.<String>value("lastToolName").orElse("");
    }

    public String lastToolArgs() {
        return this.<String>value("lastToolArgs").orElse("");
    }

    public int fatalErrorCount() {
        return counter(StateKeys.FATAL_ERROR_COUNT);
    }

    public Long userId() {
        return this.<Number>value("userId").map(Number::longValue).orElse(null);
    }

    public String errorContext() {
        return this.<String>value("errorContext").orElse("");
    }

    public int emptyResultRetries() {
        return counter(StateKeys.EMPTY_RESULT_RETRIES);
    }

    public int replanCount() {
        return counter(StateKeys.REPLAN_COUNT);
    }

    public int toolCallCount() {
        return counter(StateKeys.TOOL_CALL_COUNT);
    }

    /**
     * 当前轮次计数器 Map 的可变副本（节点增改后整体写回 {@code StateKeys.COUNTERS}）。
     * 读取统一走 {@link #counter(String)}（含旧 checkpoint 顶层标量兼容）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> counters() {
        Object raw = data().get(StateKeys.COUNTERS);
        if (raw instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    /** 读计数器：优先 counters Map，兼容旧 checkpoint 的顶层标量字段。 */
    private int counter(String key) {
        Object raw = data().get(StateKeys.COUNTERS);
        if (raw instanceof Map<?, ?> m && m.get(key) instanceof Number n) {
            return n.intValue();
        }
        return this.<Number>value(key).map(Number::intValue).orElse(0);
    }

    public boolean pendingConfirmation() {
        return this.<Boolean>value("pendingConfirmation").orElse(false);
    }

    public String confirmationPrompt() {
        return this.<String>value("confirmationPrompt").orElse("");
    }

    public String userChoice() {
        return this.<String>value("userChoice").orElse("");
    }

    /** 待确认的写操作（JSON：{"tool":..., "args":{...}}），写操作程序级确认用 */
    public String pendingWrite() {
        return this.<String>value("pendingWrite").orElse("");
    }

    /** 待用户从多个选项选择的选项（JSON 数组：[{"label":..., "value":...}]） */
    public String pendingOptions() {
        return this.<String>value("pendingOptions").orElse("");
    }

    /** 用户当前定位（前端地区中心坐标，如 "经度119.3026，纬度26.0855"） */
    public String userLocation() {
        return this.<String>value("userLocation").orElse("");
    }

    /** 用户当前地区 id（1拱墅/2鼓楼，前端地区选择器），供搜索工具按地区过滤 */
    public Long userDistrictId() {
        return this.<Number>value("userDistrictId").map(Number::longValue).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> scratchpad() {
        return this.<Map<String, Object>>value("scratchpad").orElse(new LinkedHashMap<>());
    }

    /**
     * 获取消息历史（List<Map<String,String>>，每个 Map 含 role/content 等字段）。
     * 通过 channel reducer 自动累加。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> messages() {
        return this.<List<Map<String, String>>>value("messages").orElse(new ArrayList<>());
    }

    // ======== 便捷方法 ========

    /** 创建一个 role=user 的消息 Map */
    public static Map<String, String> userMsg(String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "user");
        m.put("content", content);
        return m;
    }

    /** 创建一个 role=assistant 的消息 Map */
    public static Map<String, String> aiMsg(String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        m.put("content", content);
        return m;
    }

    /** 创建一个 role=tool 的消息 Map（工具执行结果，标准消息通道） */
    public static Map<String, String> toolMsg(String toolName, String toolCallId, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("toolName", toolName);
        m.put("toolCallId", toolCallId);
        m.put("content", content);
        return m;
    }

    /** 创建一个 role=assistant 且带 tool_calls 的消息 Map（JSON 数组字符串） */
    public static Map<String, String> assistantToolMsg(String toolCallsJson) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        m.put("toolCalls", toolCallsJson);
        return m;
    }

    /** 创建一个 role=tool 的错误消息 Map */
    public static Map<String, String> toolErrorMsg(String toolName, String toolCallId, String content) {
        Map<String, String> m = toolMsg(toolName, toolCallId, content);
        m.put("isError", "true");
        return m;
    }
}
