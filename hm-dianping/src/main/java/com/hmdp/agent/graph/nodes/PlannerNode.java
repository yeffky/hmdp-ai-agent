package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Planner Node — 分析意图，拆解任务。
 * 实现 NodeAction<ReActAgentState>，类型安全。
 */
public class PlannerNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);
    private final OpenAiChatModel model;
    private final int maxIterations;

    public PlannerNode(OpenAiChatModel model, int maxIterations) {
        this.model = model;
        this.maxIterations = maxIterations;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        int iter = state.iteration() + 1;
        if (iter > maxIterations) {
            log.warn("Planner: max iterations reached ({})", maxIterations);
            return Map.of("iteration", iter, "nextNode", "answer");
        }

        String query = state.userQuery();
        Map<String, Object> sp = state.scratchpad();
        String compressedSummary = state.compressedSummary();
        List<Map<String, String>> messages = state.messages();

        // 注入上下文到 prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("分析此查询，判断简单/复杂。\n");

        // 历史对话（最近的消息）
        if (messages != null && !messages.isEmpty()) {
            prompt.append("## 对话历史\n");
            for (Map<String, String> m : messages) {
                prompt.append(m.getOrDefault("role", "?")).append(": ")
                      .append(m.getOrDefault("content", "")).append("\n");
            }
            prompt.append("\n");
        }
        // 压缩摘要（长期记忆）
        if (compressedSummary != null && !compressedSummary.isEmpty()) {
            prompt.append("## 历史摘要\n").append(compressedSummary).append("\n\n");
        }
        prompt.append("## 当前请求\n用户: ").append(query).append("\n");
        prompt.append("已有信息: ").append(sp).append("\n");
        prompt.append("\n输出: {\"complex\": false} 或 {\"complex\": true, \"plan\": [...]}\n");
        prompt.append("可用工具: searchKnowledge, queryMyOrders, dynamicQuery, geoSearch, shopDetail");

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是任务规划器。只输出JSON。"),
                    UserMessage.from(prompt.toString())));
            String raw = resp.aiMessage().text();
            log.info("Planner (iter {}): {}", iter, raw);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("iteration", iter);
            result.put("planJson", raw);
            result.put("nextNode", "executor");
            return result;
        } catch (Exception e) {
            log.error("Planner failed at iter {}", iter, e);
            return Map.of("iteration", iter, "nextNode", "answer",
                    "finalAnswer", "规划失败: " + e.getMessage());
        }
    }
}
