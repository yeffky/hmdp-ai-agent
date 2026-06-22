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
 * Planner Node — 分析意图，拆解任务。输入/输出都是 Map。
 */
public class PlannerNode {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);
    private final OpenAiChatModel model;
    private final int maxIterations;

    public PlannerNode(OpenAiChatModel model, int maxIterations) {
        this.model = model;
        this.maxIterations = maxIterations;
    }

    public Map<String, Object> execute(Map<String, Object> state) {
        int iter = ((Number) state.getOrDefault("iteration", 0)).intValue() + 1;
        if (iter > maxIterations) {
            return Map.of("iteration", iter, "nextNode", "answer");
        }

        String query = (String) state.getOrDefault("userQuery", "");
        Object sp = state.getOrDefault("scratchpad", new LinkedHashMap<>());

        String prompt = "分析此查询，判断简单/复杂。\n" +
                "用户: " + query + "\n已有: " + sp + "\n" +
                "输出: {\"complex\": false} 或 {\"complex\": true, \"plan\": [...]}\n" +
                "工具: searchKnowledge, queryMyOrders, dynamicQuery, geoSearch, shopDetail";

        try {
            Response<AiMessage> resp = model.generate(List.of(
                    SystemMessage.from("你是任务规划器。只输出JSON。"),
                    UserMessage.from(prompt)));
            String raw = resp.content().text();
            log.info("Planner: {}", raw);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("iteration", iter);
            result.put("planJson", raw);
            result.put("nextNode", "executor");
            return result;
        } catch (Exception e) {
            log.error("Planner failed", e);
            return Map.of("iteration", iter, "nextNode", "answer",
                    "finalAnswer", "规划失败: " + e.getMessage());
        }
    }
}
