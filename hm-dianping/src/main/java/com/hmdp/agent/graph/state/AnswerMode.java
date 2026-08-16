package com.hmdp.agent.graph.state;

/**
 * 最终回答的模式（输出管线显式化）。
 * 由 {@code finalAnswer} 的值推导：哨兵 → STREAMING/ERROR，非空 → PRESET，空 → GENERATE。
 */
public enum AnswerMode {

    /** 需组装 prompt 由 LLM 生成（最终回答，Controller 流式输出） */
    GENERATE,

    /** 上游已设置 streamingPrompt，透传给 Controller 流式生成 */
    STREAMING,

    /** 预设回答（ask_user 确认问题 / 写操作确认 / 直接回答） */
    PRESET,

    /** 错误降级回答 */
    ERROR;

    public static AnswerMode fromFinalAnswer(String finalAnswer) {
        if (StateKeys.SENTINEL_STREAMING.equals(finalAnswer)) return STREAMING;
        if (StateKeys.SENTINEL_ERROR.equals(finalAnswer)) return ERROR;
        if (finalAnswer != null && !finalAnswer.isEmpty()) return PRESET;
        return GENERATE;
    }
}
