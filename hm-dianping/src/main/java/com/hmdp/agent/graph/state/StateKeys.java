package com.hmdp.agent.graph.state;

/**
 * 图状态 + scratchpad 的 key 常量，替代散落的魔法字符串。
 */
public final class StateKeys {

    private StateKeys() {}

    // ===== 顶层状态（与 ReActAgentState 访问器对应） =====
    public static final String SESSION_ID = "sessionId";
    public static final String USER_QUERY = "userQuery";
    public static final String PLAN = "plan";
    public static final String SELECTED_SKILLS = "selectedSkills"; // Planner 语义选中的技能名列表（JSON 数组）
    public static final String FINAL_ANSWER = "finalAnswer";
    public static final String NEXT_NODE = "nextNode";
    public static final String COMPRESSED_SUMMARY = "compressedSummary";
    /** 本轮证据（AgentNode 收尾时从 messages 提取的工具结果 + 卡片引用，供 Planner replan 判断 / Answer 安全网） */
    public static final String ROUND_EVIDENCE = "roundEvidence";
    public static final String CONTEXT_BLOCK = "contextBlock";
    /** 不含对话历史段的上下文块（供 Agent 决策/Answer 注入——历史由 trimmed messages 承担，避免与 contextBlock 重复） */
    public static final String CONTEXT_BLOCK_NO_HISTORY = "contextBlockNoHistory";
    public static final String STREAMING_PROMPT = "streamingPrompt";
    public static final String ERROR_CATEGORY = "errorCategory";
    public static final String ERROR_CONTEXT = "errorContext";
    public static final String LAST_TOOL_NAME = "lastToolName";
    public static final String LAST_TOOL_ARGS = "lastToolArgs";
    public static final String PENDING_CONFIRMATION = "pendingConfirmation";
    public static final String CONFIRMATION_PROMPT = "confirmationPrompt";
    public static final String USER_CHOICE = "userChoice";
    public static final String PENDING_WRITE = "pendingWrite";
    public static final String PENDING_OPTIONS = "pendingOptions"; // 让用户从多个选项选择（JSON: [{label,value}]）
    public static final String USER_ID = "userId";
    public static final String USER_LOCATION = "userLocation";
    public static final String USER_DISTRICT_ID = "userDistrictId"; // 用户当前地区 id（1拱墅/2鼓楼）
    public static final String SCRATCHPAD = "scratchpad";
    public static final String MESSAGES = "messages";

    // ===== 轮次计数器（单一 counters Map channel，轮间由 ContextNode 重置） =====
    /** 图状态中承载全部轮次计数器的 Map channel 名 */
    public static final String COUNTERS = "counters";
    /** Planner 执行轮数（超过 max-iterations 直接 answer） */
    public static final String ITERATION = "iteration";
    /** 工具执行重试次数（当前未使用，保留兼容） */
    public static final String RETRY_COUNT = "retryCount";
    /** 致命错误累计（当前未使用，保留兼容） */
    public static final String FATAL_ERROR_COUNT = "fatalErrorCount";
    /** 空结果重试次数（Text2SQL 门槛 / 失败沉淀判断） */
    public static final String EMPTY_RESULT_RETRIES = "emptyResultRetries";
    /** 重新规划次数（超过 1 次后不再 replan，直接基于已收集信息回答） */
    public static final String REPLAN_COUNT = "replanCount";
    /** 本轮工具调用总数（超过 maxRetries*2 后停止尝试，LLM 总结收尾） */
    public static final String TOOL_CALL_COUNT = "toolCallCount";

    // ===== scratchpad 内部 key =====
    public static final String SP_LAST_TOOL = "_last_tool";
    public static final String SP_LAST_ARGS = "_last_args";
    public static final String SP_LAST_RESULT = "_last_result";
    public static final String SP_LAST_RESULT_FULL = "_last_result_full"; // 全量工具结果（卡片解析用，不被截断）
    public static final String SP_ERROR = "error";
    public static final String SP_ASK_USER_MISSING = "ask_user_missing";
    public static final String SP_TOOL_CALL_COUNT_BEFORE = "_tool_call_count_before";
    /** L2/L3 领域规则（匹配 skill 的 SOP + references）——只在 Agent 执行阶段注入，Planner 阶段不暴露 */
    public static final String SP_DOMAIN_RULES = "_domain_rules";

    // ===== 哨兵值 =====
    public static final String SENTINEL_STREAMING = "__STREAMING__";
    public static final String SENTINEL_ERROR = "__ERROR__";

    /** scratchpad 结果 key 前缀：result_1 / result_2 ... */
    public static final String SP_RESULT_PREFIX = "result_";
}
