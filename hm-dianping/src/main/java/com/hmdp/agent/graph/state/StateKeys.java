package com.hmdp.agent.graph.state;

/**
 * 图状态 + scratchpad 的 key 常量，替代散落的魔法字符串。
 */
public final class StateKeys {

    private StateKeys() {}

    // ===== 顶层状态（与 ReActAgentState 访问器对应） =====
    public static final String SESSION_ID = "sessionId";
    public static final String USER_QUERY = "userQuery";
    public static final String PLAN_JSON = "planJson";
    public static final String REMAIN_PLAN = "remainPlan";
    public static final String FINAL_ANSWER = "finalAnswer";
    public static final String NEXT_NODE = "nextNode";
    public static final String COMPRESSED_SUMMARY = "compressedSummary";
    public static final String OBSERVER_REPORT = "observerReport";
    public static final String OBSERVER_FEEDBACK = "observerFeedback";
    public static final String CONTEXT_BLOCK = "contextBlock";
    public static final String STREAMING_PROMPT = "streamingPrompt";
    public static final String ITERATION = "iteration";
    public static final String RETRY_COUNT = "retryCount";
    public static final String ERROR_CATEGORY = "errorCategory";
    public static final String ERROR_CONTEXT = "errorContext";
    public static final String LAST_TOOL_NAME = "lastToolName";
    public static final String LAST_TOOL_ARGS = "lastToolArgs";
    public static final String FATAL_ERROR_COUNT = "fatalErrorCount";
    public static final String REPLAN_COUNT = "replanCount";
    public static final String TOOL_CALL_COUNT = "toolCallCount";
    public static final String PENDING_CONFIRMATION = "pendingConfirmation";
    public static final String CONFIRMATION_PROMPT = "confirmationPrompt";
    public static final String USER_ID = "userId";
    public static final String SCRATCHPAD = "scratchpad";
    public static final String MESSAGES = "messages";

    // ===== scratchpad 内部 key =====
    public static final String SP_LAST_TOOL = "_last_tool";
    public static final String SP_LAST_ARGS = "_last_args";
    public static final String SP_LAST_RESULT = "_last_result";
    public static final String SP_ERROR = "error";
    public static final String SP_ASK_USER_MISSING = "ask_user_missing";
    public static final String SP_TOOL_CALL_COUNT_BEFORE = "_tool_call_count_before";

    // ===== 哨兵值 =====
    public static final String SENTINEL_STREAMING = "__STREAMING__";
    public static final String SENTINEL_ERROR = "__ERROR__";

    /** scratchpad 结果 key 前缀：result_1 / result_2 ... */
    public static final String SP_RESULT_PREFIX = "result_";
}
