package com.hmdp.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AgentConfig：校验 sync / streaming 两个模型均直连 DeepSeek。
 *
 * <p>历史：sync 模型曾走本地代理（role=function->tool 转换），代理已随
 * LangChain4j 1.16.2 原生 role=tool 支持而删除（DeepSeekProxyController 已移除），
 * 两个模型都直连 {@code deepseek.base-url} / {@code deepseek.streaming-base-url}。
 */
class AgentConfigTest {

    private static final String DIRECT_URL = "https://api.deepseek.com";

    private AgentConfig buildConfig() {
        AgentConfig cfg = new AgentConfig();
        ReflectionTestUtils.setField(cfg, "apiKey", "sk-test");
        ReflectionTestUtils.setField(cfg, "baseUrl", DIRECT_URL);
        ReflectionTestUtils.setField(cfg, "streamingBaseUrl", DIRECT_URL);
        ReflectionTestUtils.setField(cfg, "model", "deepseek-chat");
        ReflectionTestUtils.setField(cfg, "temperature", 0.7);
        ReflectionTestUtils.setField(cfg, "maxTokens", 2000);
        ReflectionTestUtils.setField(cfg, "timeoutSeconds", 60);
        // openAiChatModel() 会注册 llmTraceListener，直接 new 一个空实例即可（方法不触发）
        ReflectionTestUtils.setField(cfg, "llmTraceListener", new LlmTraceListener());
        return cfg;
    }

    /** 反射读取模型内部 OpenAiClient 的 baseUrl；sync 模型被 CachingChatModel 包装，需先取 delegate */
    private static String readBaseUrl(Object model) throws Exception {
        Object unwrapped = model;
        try {
            unwrapped = readField(model, "delegate");
        } catch (NoSuchFieldException e) {
            // streaming 模型无 delegate，直接用原对象
        }
        Object client = readField(unwrapped, "client");
        return (String) readField(client, "baseUrl");
    }

    private static Object readField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    void syncModelUsesConfiguredDirectUrl() throws Exception {
        ChatModel model = buildConfig().openAiChatModel();
        assertEquals(DIRECT_URL, readBaseUrl(model),
                "sync 模型应直连 DeepSeek（代理已删除）");
    }

    @Test
    void streamingModelUsesStreamingBaseUrl() throws Exception {
        OpenAiStreamingChatModel model = buildConfig().openAiStreamingChatModel();
        assertEquals(DIRECT_URL, readBaseUrl(model),
                "streaming 模型应直连 DeepSeek，保证 SSE 逐 token");
    }
}
