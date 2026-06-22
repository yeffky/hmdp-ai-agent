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
 * Observer Node — 评估信息充足性，路由到 plan 或 answer。
 */
public class ObserverNode {

    private static final Logger log = LoggerFactory.getLogger(ObserverNode.class);
    private final OpenAiChatModel model;
    private final int maxIterations;

    public ObserverNode(OpenAiChatModel model, int maxIterations) {
        this.model = model;
        this.maxIterations = maxIterations;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> state) {
        int fails = ((Number) state.getOrDefault("toolFailures", 0)).intValue();
        int iter = ((Number) state.getOrDefault("iteration", 0)).intValue();

        // 熔断
        if (fails >= 2) {
            log.warn("熔断: fails={}", fails);
            return Map.of("nextNode", "answer");
        }
        if (iter >= maxIterations) {
            log.warn("超限: iter={}", iter);
            return Map.of("nextNode", "answer");
        }

        Map<String, Object> sp = (Map<String, Object>) state.getOrDefault("scratchpad", Collections.emptyMap());
        if (sp.isEmpty()) {
            return Map.of("nextNode", "plan");
        }

        String prompt = "用户问题: " + state.getOrDefault("userQuery", "") +
                "\n收集信息: " + sp +
                "\n\n信息足够回答吗？够了回 'done'，不够回 'more: <需要什么>'";

        try {
            Response<AiMessage> resp = model.generate(List.of(
                    SystemMessage.from("你是质量检查器。只回 done 或 more:"),
                    UserMessage.from(prompt)));
            String raw = resp.content().text().trim().toLowerCase();
            log.info("Observer: {}", raw);

            if (raw.startsWith("done") || raw.contains("足够")) {
                return Map.of("nextNode", "answer");
            }
            return Map.of("nextNode", "plan",
                    "observerFeedback", raw);
        } catch (Exception e) {
            return Map.of("nextNode", "answer");
        }
    }
}
