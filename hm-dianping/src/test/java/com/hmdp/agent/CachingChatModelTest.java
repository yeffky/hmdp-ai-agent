package com.hmdp.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LLM 响应缓存：无工具调用且非结构化输出的请求按消息哈希缓存；
 * 带工具调用 / 带 responseFormat（Planner 等结构化输出）的请求一律不缓存。
 */
class CachingChatModelTest {

    private ChatModel delegate;
    private CachingChatModel caching;

    @BeforeEach
    void setUp() {
        delegate = mock(ChatModel.class);
        when(delegate.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("你好")).build());
        caching = new CachingChatModel(delegate, 60_000L);
    }

    private ChatRequest plainRequest() {
        return ChatRequest.builder()
                .messages(List.of(UserMessage.from("附近有什么火锅店")))
                .build();
    }

    @Test
    void plainRequest_secondCallHitsCache() {
        assertEquals("你好", caching.chat(plainRequest()).aiMessage().text());
        assertEquals("你好", caching.chat(plainRequest()).aiMessage().text());
        verify(delegate, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void responseFormatRequest_neverCached() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(UserMessage.from("分析意图并制定计划")))
                .responseFormat(ResponseFormat.JSON)
                .build();
        caching.chat(req);
        caching.chat(req);
        // Planner 等结构化输出意图随上下文变化，绝不能命中缓存返回过期规划
        verify(delegate, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void toolCallRequest_neverCached() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(UserMessage.from("帮我取号")))
                .toolSpecifications(List.of(ToolSpecification.builder()
                        .name("takeQueueNumber").description("取号").build()))
                .build();
        caching.chat(req);
        caching.chat(req);
        verify(delegate, times(2)).chat(any(ChatRequest.class));
    }
}
