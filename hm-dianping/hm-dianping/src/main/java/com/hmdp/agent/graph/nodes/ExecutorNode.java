package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.tool.KnowledgeRetrievalTool;
import com.hmdp.agent.tool.OrderQueryTool;
import com.hmdp.agent.tool.graph.DynamicQueryTool;
import com.hmdp.agent.tool.graph.GeoSearchTool;
import com.hmdp.agent.tool.graph.ShopDetailTool;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Executor Node — LLM 选工具 → 执行 → 结果写入 scratchpad。
 */
public class ExecutorNode {

    private static final Logger log = LoggerFactory.getLogger(ExecutorNode.class);
    private final OpenAiChatModel model;
    private final KnowledgeRetrievalTool retrievalTool;
    private final OrderQueryTool orderTool;
    private final DynamicQueryTool dynamicQueryTool;
    private final GeoSearchTool geoTool;
    private final ShopDetailTool detailTool;

    public ExecutorNode(OpenAiChatModel model,
                         KnowledgeRetrievalTool retrievalTool, OrderQueryTool orderTool,
                         DynamicQueryTool dynamicQueryTool, GeoSearchTool geoTool,
                         ShopDetailTool detailTool) {
        this.model = model;
        this.retrievalTool = retrievalTool;
        this.orderTool = orderTool;
        this.dynamicQueryTool = dynamicQueryTool;
        this.geoTool = geoTool;
        this.detailTool = detailTool;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> state) {
        String query = (String) state.getOrDefault("userQuery", "");
        String plan = (String) state.getOrDefault("planJson", "{}");
        Map<String, Object> scratchpad = (Map<String, Object>) state.getOrDefault("scratchpad", new LinkedHashMap<>());

        String prompt = "选择一个工具执行。输出JSON: {\"tool\": \"工具名\", \"args\": {...}}\n\n" +
                "用户: " + query + "\n计划: " + plan + "\n已有: " + scratchpad + "\n\n" +
                "工具: searchKnowledge(query), queryMyOrders(status), dynamicQuery(filters,sort,order,limit), " +
                "geoSearch(typeId,x,y,radius), shopDetail(shopId)";

        try {
            Response<AiMessage> resp = model.generate(List.of(
                    SystemMessage.from("你是工具调度器。只输出JSON。"),
                    UserMessage.from(prompt)));
            String raw = resp.content().text().trim();
            log.info("Executor call: {}", raw);

            String result = dispatch(raw);
            scratchpad.put("result_" + (scratchpad.size() + 1), result);
            return Map.of("scratchpad", scratchpad, "toolFailures", 0, "nextNode", "observer");
        } catch (Exception e) {
            log.error("Executor failed", e);
            int fails = ((Number) state.getOrDefault("toolFailures", 0)).intValue() + 1;
            scratchpad.put("error", e.getMessage());
            return Map.of("scratchpad", scratchpad, "toolFailures", fails, "nextNode", "observer");
        }
    }

    private String dispatch(String json) {
        String tool = extractStr(json, "tool");
        String argsStr = extractObj(json, "args");

        switch (tool) {
            case "searchKnowledge":
                return retrievalTool.searchKnowledge(extractStr(argsStr, "query"));
            case "queryMyOrders":
                String status = extractStr(argsStr, "status");
                return orderTool.queryMyOrders(status.isEmpty() ? null : Integer.parseInt(status));
            case "dynamicQuery":
                return dynamicQueryTool.query(argsStr);
            case "geoSearch":
                return geoTool.search(
                        Integer.parseInt(extractStr(argsStr, "typeId")),
                        Double.parseDouble(extractStr(argsStr, "x")),
                        Double.parseDouble(extractStr(argsStr, "y")),
                        argsStr.contains("radius") ? Integer.parseInt(extractStr(argsStr, "radius")) : 5000);
            case "shopDetail":
                return detailTool.getDetail(Long.parseLong(extractStr(argsStr, "shopId")));
            default:
                return "Unknown tool: " + tool;
        }
    }

    private String extractStr(String json, String key) {
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) return "";
        int s = i + key.length() + 3;
        while (s < json.length() && json.charAt(s) == ' ') s++;
        if (s < json.length() && json.charAt(s) == '"') {
            int e = json.indexOf("\"", s + 1);
            return e > s ? json.substring(s + 1, e) : "";
        }
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '.' || json.charAt(e) == '-')) e++;
        return e > s ? json.substring(s, e) : "";
    }

    private String extractObj(String json, String key) {
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) return "{}";
        int s = i + key.length() + 3;
        while (s < json.length() && json.charAt(s) == ' ') s++;
        if (s < json.length() && json.charAt(s) == '{') {
            int d = 1, e = s + 1;
            while (e < json.length() && d > 0) {
                if (json.charAt(e) == '{') d++;
                else if (json.charAt(e) == '}') d--;
                e++;
            }
            return json.substring(s, e);
        }
        return "{}";
    }
}
