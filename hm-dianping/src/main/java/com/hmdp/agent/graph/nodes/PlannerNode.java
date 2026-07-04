package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Planner Node — 纯规划，不做充分性判断。
 *
 * <p>两种模式：
 * <ul>
 *   <li><b>初始规划</b>（observerReport 为空）：分析用户意图，制定执行计划</li>
 *   <li><b>重规划</b>（observerReport 有内容 + observerFeedback 来自 JudgeNode）：
 *       旧计划已失败，必须换思路，不能重复已失败的路径</li>
 * </ul>
 */
public class PlannerNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);
    private final OpenAiChatModel model;
    private final int maxIterations;
    private final String toolListBlock;
    private final String toolNamesBlock;

    public PlannerNode(OpenAiChatModel model, int maxIterations, LC4jToolService toolService) {
        this.model = model;
        this.maxIterations = maxIterations;
        this.toolListBlock = buildToolListBlock(toolService);
        this.toolNamesBlock = buildToolNamesBlock(toolService);
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        int iter = state.iteration() + 1;
        if (iter > maxIterations) {
            log.warn("Planner: max iterations reached ({})", maxIterations);
            return Map.of("iteration", iter, "nextNode", "answer");
        }

        String query = state.userQuery();
        String observerReport = state.observerReport();
        String feedback = state.observerFeedback();
        boolean isReplan = (observerReport != null && !observerReport.isEmpty())
                || (feedback != null && !feedback.isEmpty());

        StringBuilder prompt = new StringBuilder();
        prompt.append(state.contextBlock());
        prompt.append("\n");

        if (isReplan) {
            // ============================================================
            // 重规划模式：旧计划失败，必须换思路
            // ============================================================
            prompt.append("## 已失败的原始计划\n").append(state.planJson()).append("\n\n");
            prompt.append("## 执行结果\n").append(observerReport).append("\n\n");
            if (feedback != null && !feedback.isEmpty()) {
                prompt.append("## Judge 反馈\n").append(feedback).append("\n\n");
            }
            prompt.append("## 用户请求\n").append(query).append("\n\n");
            prompt.append("上述计划未能获取足够信息。请制定一个**全新**的执行计划：\n");
            prompt.append("- 不要重复原始计划中已失败的步骤，换一个查询思路\n");
            prompt.append("- 例如：如果之前只查了单表，考虑联表；如果之前用精确匹配，改用模糊匹配\n");
            prompt.append("- 如果确实无法通过任何工具获取所需数据，输出 ask_user\n");
            prompt.append("- 输出JSON：{\"intent\":\"用户意图\",\"complex\":true,\"plan\":[\"第1步：...\",\"第2步：...\"]}\n");
            prompt.append("- 或：{\"ask_user\": \"需要用户提供什么信息\"}\n\n");
            prompt.append(toolListBlock).append("\n");
        } else {
            // ============================================================
            // 初始规划模式
            // ============================================================
            prompt.append("## 当前请求\n用户: ").append(query).append("\n");
            prompt.append("已有数据: ").append(formatToolResults(state.scratchpad())).append("\n\n");
            prompt.append("分析用户意图并制定计划。输出JSON：\n");
            prompt.append("简单问题：{\"intent\":\"用户意图一句话\",\"complex\":false}\n");
            prompt.append("需要工具：{\"intent\":\"用户意图\",\"complex\":true,\"plan\":[\"第1步：用X工具做Y，因为Z\",\"第2步：...\"]}\n");
            prompt.append("缺少用户信息且无法通过工具获取（如地理位置、登录凭证）：{\"ask_user\": \"需要补充什么信息\"}\n\n");
            prompt.append(toolListBlock).append("\n");
        }

        try {
            String promptStr = prompt.toString();
            log.info("Planner prompt (iter {}, replan={}):\n{}", iter, isReplan, promptStr);
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是任务规划器。只输出JSON。如果是重规划，必须换思路不重复失败路径。"),
                    UserMessage.from(promptStr)));
            String raw = resp.aiMessage().text();
            log.info("Planner (iter {}): {}", iter, raw);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("iteration", iter);
            result.put("planJson", raw);

            if (raw.contains("\"ask_user\"")) {
                String askMsg = extractJsonStr(raw, "ask_user");
                result.put("finalAnswer", askMsg != null ? askMsg : "请提供更多信息");
                result.put("nextNode", "answer");
            } else {
                result.put("nextNode", "executor");
                result.put("remainPlan", raw);
            }
            return result;
        } catch (Exception e) {
            log.error("Planner failed at iter {}", iter, e);
            return Map.of("iteration", iter, "nextNode", "answer",
                    "finalAnswer", "规划失败: " + e.getMessage());
        }
    }

    private static String extractJsonStr(String json, String key) {
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) return null;
        int s = json.indexOf("\"", i + key.length() + 3);
        if (s < 0) return null;
        int e = json.indexOf("\"", s + 1);
        return e > s ? json.substring(s + 1, e) : null;
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

    private static String buildToolListBlock(LC4jToolService toolService) {
        StringBuilder sb = new StringBuilder("## 可用工具\n\n");
        List<ToolSpecification> specs = toolService.toolSpecifications();
        for (int i = 0; i < specs.size(); i++) {
            ToolSpecification ts = specs.get(i);
            sb.append(i + 1).append(". **").append(ts.name()).append("**：").append(ts.description()).append("\n");
            if (ts.parameters() != null && ts.parameters().properties() != null) {
                List<String> required = ts.parameters().required() != null
                        ? ts.parameters().required() : List.of();
                ts.parameters().properties().forEach((name, prop) -> {
                    String marker = required.contains(name) ? "必填" : "可选";
                    sb.append("   - `").append(name).append("`(")
                      .append(schemaType(prop)).append(") [").append(marker).append("]");
                    if (prop.description() != null && !prop.description().isEmpty()) {
                        sb.append(" — ").append(prop.description());
                    }
                    sb.append("\n");
                });
            }
            sb.append("\n");
        }
        sb.append("## 规划规则\n");
        sb.append("- 计划中只写可执行的工具调用步骤，不要写条件分支（如「如果为空则...」）或用户通知步骤（如「告知用户」）。\n");
        sb.append("- 工具执行后系统会自动观察结果并判断下一步，你不需要在计划里预判各种分支。\n");
        sb.append("- 制定计划前，先检查工具所需的 [必填] 参数。如果用户未提供，计划中必须包含获取该参数的步骤。\n");
        sb.append("- 例如：用户给的是商铺名称但工具需要 shopId → 计划中必须先查询商铺ID。\n");
        sb.append("- 如果 [必填] 参数无法通过任何工具获取（如用户地理位置坐标、登录凭证），第一步必须输出 ask_user 向用户索要，不要规划无法执行的步骤。\n");
        return sb.toString();
    }

    private static String buildToolNamesBlock(LC4jToolService toolService) {
        return "可用工具：" + toolService.toolSpecifications().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.joining(" / "));
    }

    private static String schemaType(JsonSchemaElement prop) {
        String className = prop.getClass().getSimpleName();
        return className.replace("Json", "").replace("Schema", "").toLowerCase();
    }

    /** 不截断版本 — AnswerNode 使用，确保最终回答能看到全部数据 */
    public static String formatToolResultsFull(Map<String, Object> sp) {
        if (sp == null || sp.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sp.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals("ask_user_missing") || k.equals("error")) continue;
            String v = e.getValue() != null ? e.getValue().toString() : "";
            sb.append(k).append(": ").append(v).append("\n");
        }
        return sb.toString();
    }
}
