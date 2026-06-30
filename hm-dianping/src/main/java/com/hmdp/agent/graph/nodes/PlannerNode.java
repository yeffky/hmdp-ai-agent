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
        String feedback = state.observerFeedback();

        StringBuilder prompt = new StringBuilder();
        prompt.append(state.contextBlock());
        prompt.append("\n");

        if (feedback != null && !feedback.isEmpty()) {
            prompt.append("## 上轮反馈\n当前缺少: ").append(feedback).append("\n");
            prompt.append("请检查「工具结果」中是否已有相关数据。如有则制定下一步工具调用计划；如确实无法获取则输出 ask_user。\n\n");
        }

        prompt.append("## 当前请求\n用户: ").append(query).append("\n");
        prompt.append("已有数据: ").append(formatToolResults(state.scratchpad())).append("\n\n");
        prompt.append("分析用户意图并制定计划。输出JSON：\n");
        prompt.append("简单问题：{\"intent\":\"用户意图一句话\",\"complex\":false}\n");
        prompt.append("需要工具：{\"intent\":\"用户意图\",\"complex\":true,\"plan\":[\"第1步：用X工具做Y，因为Z\",\"第2步：...\"]}\n\n");
        prompt.append("可用工具：\n");
        prompt.append("- searchKnowledge: 仅查平台规则/操作流程（如何领券、怎么退款等），不查商家/商品数据\n");
        prompt.append("- queryMyOrders: 查当前用户的优惠券订单\n");
        prompt.append("- text2Sql: 自然语言查询数据库（商家/优惠券/用户信息等）\n");
        prompt.append("- geoSearch: 按地理位置搜索附近商家\n");
        prompt.append("- shopDetail: 查单个商家详细信息\n");

        try {
            String promptStr = prompt.toString();
            log.info("Planner prompt (iter {}):\n{}", iter, promptStr);
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是任务规划器。先分析用户意图，再拆解为具体步骤。每步要说清楚用什么工具、做什么、为什么。只输出JSON。"),
                    UserMessage.from(promptStr)));
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

    static String formatToolResults(Map<String, Object> sp) {
        if (sp == null || sp.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sp.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals("ask_user_missing") || k.equals("error")) continue;
            String v = e.getValue() != null ? e.getValue().toString() : "";
            if (v.length() > 800) v = v.substring(0, 800) + "...";
            sb.append(k).append(": ").append(v).append("\n");
        }
        return sb.toString();
    }
}
