package com.hmdp.agent.graph.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * LLM 提示词模板 — 从 resources/prompts/ 加载，改提示词无需改 Java 代码。
 */
public final class PromptTemplates {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplates.class);

    public static final String PLANNER_SYSTEM = load("planner-system.txt");
    public static final String EXECUTOR_SYSTEM = load("executor-system.txt");
    public static final String ERROR_CLASSIFIER_SYSTEM = load("error-classifier-system.txt");
    public static final String ERROR_CLASSIFIER_PROMPT = load("error-classifier-prompt.txt");
    public static final String JUDGE_SYSTEM = load("judge-system.txt");
    public static final String ERROR_ANSWER_SYSTEM = load("error-answer-system.txt");
    public static final String OBSERVER_VALIDATE_SYSTEM = load("observer-validate-system.txt");

    private PromptTemplates() {}

    private static String load(String name) {
        try (InputStream in = PromptTemplates.class.getClassLoader()
                .getResourceAsStream("prompts/" + name)) {
            if (in == null) {
                log.error("Prompt template not found: {}", name);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", name, e);
            return "";
        }
    }
}
