package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.NodeNames;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AnswerNode 挂起分支：
 * - 写确认（pendingWrite）→ 直接 END，不生成文本（Controller 发确认卡片）；
 * - 选项选择（pendingOptions）→ 生成流式叙述 prompt（选择前的介绍语），完成后由 Controller 发按钮。
 */
class AnswerNodeTest {

    private final AnswerNode answerNode = new AnswerNode(null); // model 字段未使用

    private ReActAgentState state(Map<String, Object> init) {
        return new ReActAgentState(init);
    }

    @Test
    void pendingWrite_pausesWithoutText() throws Exception {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put(StateKeys.PENDING_WRITE, "{\"tool\":\"takeQueueNumber\",\"args\":{\"shopId\":1}}");
        Map<String, Object> out = answerNode.apply(state(init));
        assertEquals(NodeNames.END, out.get(StateKeys.NEXT_NODE));
        assertNull(out.get(StateKeys.STREAMING_PROMPT));
    }

    @Test
    void pendingOptions_generatesNarrationStreaming() throws Exception {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put(StateKeys.PENDING_OPTIONS,
                "[{\"label\":\"朱富贵火锅\",\"value\":\"朱富贵火锅\"},{\"label\":\"小龙坎\",\"value\":\"小龙坎\"}]");
        init.put(StateKeys.CONFIRMATION_PROMPT, "为您找到两家火锅店：朱富贵火锅 评分4.5；小龙坎 评分4.3");
        Map<String, Object> out = answerNode.apply(state(init));
        assertEquals(StateKeys.SENTINEL_STREAMING, out.get(StateKeys.FINAL_ANSWER));
        String prompt = (String) out.get(StateKeys.STREAMING_PROMPT);
        assertTrue(prompt.contains("叙述"), prompt);
        assertTrue(prompt.contains("朱富贵火锅"), prompt); // 候选概要注入叙述上下文
    }

    @Test
    void pendingOptions_blankOptions_stillNarrates() throws Exception {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put(StateKeys.PENDING_OPTIONS, "[]");
        init.put(StateKeys.CONFIRMATION_PROMPT, "请选择一家店");
        Map<String, Object> out = answerNode.apply(state(init));
        assertEquals(StateKeys.SENTINEL_STREAMING, out.get(StateKeys.FINAL_ANSWER));
        assertTrue(((String) out.get(StateKeys.STREAMING_PROMPT)).contains("叙述"));
    }
}
