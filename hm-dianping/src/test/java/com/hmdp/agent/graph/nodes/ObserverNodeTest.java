package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ObserverNode 确认恢复分支 — 无论用户回复是否匹配，续跑时都必须清除过期的 finalAnswer，
 * 否则数据足够后 AnswerNode 仍会回复上一次 ask_user 的旧问题（回归：海底捞排队 bug）。
 */
class ObserverNodeTest {

    private static OpenAiChatModel model(boolean matches) {
        OpenAiChatModel m = mock(OpenAiChatModel.class);
        when(m.chat(anyList())).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(matches ? "YES" : "NO"))
                .build());
        return m;
    }

    private static ReActAgentState confirmationState(String userChoice) {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put("userChoice", userChoice);
        init.put("confirmationPrompt", "请告诉我想查询哪家海底捞门店");
        init.put("pendingConfirmation", false);
        init.put("scratchpad", new LinkedHashMap<String, Object>());
        init.put("nextNode", "observer");
        return new ReActAgentState(init);
    }

    @Test
    void validatedResponseClearsStaleFinalAnswerAndContinues() throws Exception {
        ObserverNode node = new ObserverNode(model(true), 8);
        Map<String, Object> result = node.apply(confirmationState("水晶城那家"));

        assertEquals("executor", result.get("nextNode"));
        assertEquals("", result.get("finalAnswer"));
        assertEquals("", result.get("userChoice"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sp = (Map<String, Object>) result.get("scratchpad");
        assertEquals("水晶城那家", sp.get("_user_response"));
    }

    @Test
    void mismatchResponseRoutesToPlannerAndClearsStaleFinalAnswer() throws Exception {
        ObserverNode node = new ObserverNode(model(false), 8);
        Map<String, Object> result = node.apply(confirmationState("帮我查一下天气"));

        assertEquals("planner", result.get("nextNode"));
        assertEquals("", result.get("finalAnswer"));
        assertEquals("", result.get("userChoice"));
    }
}
