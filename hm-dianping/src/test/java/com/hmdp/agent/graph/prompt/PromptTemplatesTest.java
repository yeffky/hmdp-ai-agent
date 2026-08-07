package com.hmdp.agent.graph.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PromptTemplates 加载 — 确保所有模板文件存在且非空（改提示词时若漏建文件会被这里抓住）。
 */
class PromptTemplatesTest {

    @Test
    void allSystemTemplatesLoaded() {
        assertFalse(PromptTemplates.PLANNER_SYSTEM.isBlank(), "planner-system.txt");
        assertFalse(PromptTemplates.EXECUTOR_SYSTEM.isBlank(), "executor-system.txt");
        assertFalse(PromptTemplates.JUDGE_SYSTEM.isBlank(), "judge-system.txt");
        assertFalse(PromptTemplates.ERROR_ANSWER_SYSTEM.isBlank(), "error-answer-system.txt");
        assertFalse(PromptTemplates.OBSERVER_VALIDATE_SYSTEM.isBlank(), "observer-validate-system.txt");
        assertFalse(PromptTemplates.ERROR_CLASSIFIER_SYSTEM.isBlank(), "error-classifier-system.txt");
    }

    @Test
    void errorClassifierPromptKeepsPlaceholders() {
        String p = PromptTemplates.ERROR_CLASSIFIER_PROMPT;
        assertFalse(p.isBlank(), "error-classifier-prompt.txt");
        assertTrue(p.contains("%s"), "必须保留 String.format 占位符");
    }
}
