package com.hmdp.agent.graph.nodes;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * askUserToChoose 恢复意图分类：
 * 点按钮（精确命中选项 value/label）→ SELECT；自由输入新话题 → LLM 判 NEW → 回 planner 重新规划。
 */
class OptionIntentResolveTest {

    private static final String OPTIONS =
            "[{\"label\":\"朱富贵火锅\",\"value\":\"朱富贵火锅\"},{\"label\":\"小龙坎\",\"value\":\"小龙坎\"}]";

    @Test
    void exactMatchOptionValue_isSelect() {
        assertEquals(AgentNode.OptionIntent.SELECT,
                AgentNode.resolveOptionIntent("朱富贵火锅", OPTIONS, null));
    }

    @Test
    void blankChoice_isSelect() {
        assertEquals(AgentNode.OptionIntent.SELECT,
                AgentNode.resolveOptionIntent("", OPTIONS, null));
        assertEquals(AgentNode.OptionIntent.SELECT,
                AgentNode.resolveOptionIntent(null, OPTIONS, null));
    }

    @Test
    void llmClassifiesNewTopic_asNew() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"intent\":\"new\",\"reason\":\"用户开启新话题\"}")).build());
        assertEquals(AgentNode.OptionIntent.NEW,
                AgentNode.resolveOptionIntent("帮我取个号", OPTIONS, model));
    }

    @Test
    void llmClassifiesSelection_asSelect() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"intent\":\"select\",\"reason\":\"选第一家\"}")).build());
        assertEquals(AgentNode.OptionIntent.SELECT,
                AgentNode.resolveOptionIntent("选第一家", OPTIONS, model));
    }

    @Test
    void llmFailure_defaultsToSelect() {
        // 模型不可用 / 调用失败 → 默认 SELECT（不打断当前任务，agent 仍有 replan 兜底）
        assertEquals(AgentNode.OptionIntent.SELECT,
                AgentNode.resolveOptionIntent("帮我取个号", OPTIONS, null));
    }
}
