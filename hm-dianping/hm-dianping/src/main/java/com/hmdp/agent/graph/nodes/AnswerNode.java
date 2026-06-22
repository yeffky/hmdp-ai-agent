package com.hmdp.agent.graph.nodes;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Answer Node — 基于 scratchpad 生成最终回答。
 */
public class AnswerNode {

    private static final Logger log = LoggerFactory.getLogger(AnswerNode.class);
    private final OpenAiChatModel model;

    public AnswerNode(OpenAiChatModel model) {
        this.model = model;
    }

    public Map<String, Object> execute(Map<String, Object> state) {
        String query = (String) state.getOrDefault("userQuery", "");
        Object sp = state.getOrDefault("scratchpad", "{}");

        String prompt = "## 用户问题\n" + query + "\n\n## 收集信息\n" + sp +
                "\n\n请基于以上信息友好回答。不足则坦诚告知。";

        try {
            Response<AiMessage> resp = model.generate(List.of(
                    SystemMessage.from("你是黑马点评AI客服小黑。友好、专业、简洁。"),
                    UserMessage.from(prompt)));
            return Map.of("finalAnswer", resp.content().text(), "nextNode", "__END__");
        } catch (Exception e) {
            log.error("Answer failed", e);
            return Map.of("finalAnswer", "抱歉，处理出错，请稍后重试。", "nextNode", "__END__");
        }
    }
}
