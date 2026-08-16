package com.hmdp.agent.graph.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AnswerMode：finalAnswer 值 → 回答模式映射（输出管线显式化）。
 */
class AnswerModeTest {

    @Test
    void mapsStreamingSentinel() {
        assertEquals(AnswerMode.STREAMING, AnswerMode.fromFinalAnswer(StateKeys.SENTINEL_STREAMING));
    }

    @Test
    void mapsErrorSentinel() {
        assertEquals(AnswerMode.ERROR, AnswerMode.fromFinalAnswer(StateKeys.SENTINEL_ERROR));
    }

    @Test
    void mapsNonEmptyToPreset() {
        assertEquals(AnswerMode.PRESET, AnswerMode.fromFinalAnswer("请提供更多信息以便查询"));
        assertEquals(AnswerMode.PRESET, AnswerMode.fromFinalAnswer("已取消该操作。"));
    }

    @Test
    void mapsEmptyAndNullToGenerate() {
        assertEquals(AnswerMode.GENERATE, AnswerMode.fromFinalAnswer(""));
        assertEquals(AnswerMode.GENERATE, AnswerMode.fromFinalAnswer(null));
    }
}
