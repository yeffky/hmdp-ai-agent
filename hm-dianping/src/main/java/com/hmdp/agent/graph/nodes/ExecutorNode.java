package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Executor Node — LLM 看 Tool Schema 选工具 → LC4jToolService 执行 → 结果写入 scratchpad。
 * 工具不再手写 dispatch，由 LangGraph4j 的 LC4jToolService 统一管理。
 */
public class ExecutorNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ExecutorNode.class);
    private final OpenAiChatModel model;
    private final LC4jToolService toolService;
    private final String toolSchemaPrompt; // 缓存生成的 tool schema prompt

    public ExecutorNode(OpenAiChatModel model, LC4jToolService toolService) {
        this.model = model;
        this.toolService = toolService;
        this.toolSchemaPrompt = buildToolSchemaPrompt();
    }

    /** 从 ToolSpecification 列表自动生成 prompt 中的工具描述 */
    private String buildToolSchemaPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用工具\n\n");
        List<ToolSpecification> specs = toolService.toolSpecifications();
        for (int i = 0; i < specs.size(); i++) {
            ToolSpecification ts = specs.get(i);
            sb.append(i + 1).append(". ").append(ts.name()).append("\n");
            sb.append("   描述: ").append(ts.description()).append("\n");
            sb.append("   参数:\n");
            if (ts.parameters() != null && ts.parameters().properties() != null) {
                // 获取必填参数列表
                List<String> required = ts.parameters().required() != null
                        ? ts.parameters().required() : List.of();
                ts.parameters().properties().forEach((name, prop) -> {
                    String marker = required.contains(name) ? " [必填]" : " [可选]";
                    sb.append("     • ").append(name)
                      .append(" (").append(schemaType(prop)).append(")")
                      .append(marker);
                    if (prop.description() != null && !prop.description().isEmpty()) {
                        sb.append(" — ").append(prop.description());
                    }
                    sb.append("\n");
                });
            }
            sb.append("\n");
        }
        sb.append("## 规则\n");
        sb.append("- [必填] 参数必须全部提供，缺一不可！如果缺少必填参数，必须输出 ask_user\n");
        sb.append("- typeId 是整数不是字符串！不确定时先调 searchKnowledge 查映射表\n");
        sb.append("- 参数不足时输出 {\"ask_user\": true, \"missing\": \"缺少什么参数\"}\n");
        sb.append("- 信息足够时输出 {\"tool\": \"工具名\", \"args\": {...}}\n");
        return sb.toString();
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        String query = state.userQuery();
        String plan = state.planJson() != null ? state.planJson() : "{}";
        Map<String, Object> scratchpad = state.scratchpad();
        List<Map<String, String>> messages = state.messages();

        StringBuilder prompt = new StringBuilder();
        prompt.append("选择一个工具执行。只输出JSON: {\"tool\": \"工具名\", \"args\": {...}}\n\n");

        // 历史对话
        if (messages != null && !messages.isEmpty()) {
            prompt.append("## 对话历史\n");
            for (Map<String, String> m : messages) {
                prompt.append(m.getOrDefault("role", "?")).append(": ")
                      .append(m.getOrDefault("content", "")).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("用户: ").append(query).append("\n");
        prompt.append("计划: ").append(plan).append("\n");
        prompt.append("已有信息: ").append(scratchpad).append("\n\n");
        prompt.append(toolSchemaPrompt);

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是工具调度器。只输出JSON。"),
                    UserMessage.from(prompt.toString())));
            String raw = resp.aiMessage().text().trim();
            log.info("Executor call: {}", raw);

            // 解析 LLM 输出
            String toolName = extractStr(raw, "tool");
            String argsStr = extractObj(raw, "args");

            if (toolName.isEmpty() || toolName.equals("ask_user")) {
                String missing = extractStr(raw, "missing");
                String question = (missing != null && !missing.isEmpty())
                        ? "请提供以下信息：" + missing
                        : "请提供更多信息以便为您查询";
                scratchpad.put("ask_user_missing", question);
                log.info("Executor asks user: {}", question);
                return Map.of("scratchpad", scratchpad,
                        "toolFailures", 0,
                        "finalAnswer", question,
                        "nextNode", "answer");  // 直接到 answer，跳过 observer
            }

            // 构造 ToolExecutionRequest 并用 LC4jToolService 执行
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(argsStr)
                    .build();

            var execResult = toolService.execute(
                    List.of(request),
                    InvocationContext.builder().build(),
                    "toolResults"  // property key for result in Command.update()
            ).get(); // 同步等待

            String result = extractToolResult(execResult);

            scratchpad.put("result_" + (scratchpad.size() + 1), result);

            // 检测工具是否返回了错误结果（如"查询失败"、"搜索失败"等）
            if (result != null && (result.contains("失败") || result.contains("异常"))) {
                log.warn("Tool {} returned error: {}", toolName,
                        result.length() > 100 ? result.substring(0, 100) : result);
                int fails = state.toolFailures() + 1;
                scratchpad.put("error", result);
                String errMsg = fails >= 2
                        ? "抱歉，查询服务暂时不可用，请稍后重试。"
                        : "抱歉，" + result;
                return Map.of("scratchpad", scratchpad,
                        "toolFailures", fails,
                        "finalAnswer", errMsg,
                        "nextNode", "answer");
            }

            Map<String, Object> mapResult = new LinkedHashMap<>();
            mapResult.put("scratchpad", scratchpad);
            mapResult.put("toolFailures", 0);
            mapResult.put("nextNode", "observer");
            return mapResult;
        } catch (Exception e) {
            log.error("Executor failed", e);
            int fails = state.toolFailures() + 1;
            String errDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            scratchpad.put("error", errDetail);

            // 尝试从异常中提取缺失的参数名
            String errMsg;
            String missingParam = extractMissingParam(errDetail);
            if (missingParam != null) {
                errMsg = "请提供以下信息：" + missingParam;
                scratchpad.put("ask_user_missing", errMsg);
            } else if (fails >= 2) {
                errMsg = "抱歉，查询服务暂时不可用，请稍后重试。";
            } else {
                errMsg = "抱歉，查询时遇到了问题，请稍后再试。";
            }
            return Map.of("scratchpad", scratchpad,
                    "toolFailures", fails,
                    "finalAnswer", errMsg,
                    "nextNode", "answer");
        }
    }

    /** 从异常消息中提取缺失的参数名 */
    private String extractMissingParam(String errMsg) {
        if (errMsg == null) return null;
        int i = errMsg.indexOf("Required parameter \"");
        if (i < 0) return null;
        i += "Required parameter \"".length();
        int j = errMsg.indexOf("\"", i);
        if (j > i) {
            String param = errMsg.substring(i, j);
            return switch (param) {
                case "x" -> "您的位置经度（或开启定位权限）";
                case "y" -> "您的位置纬度（或开启定位权限）";
                default -> param + "参数";
            };
        }
        return null;
    }

    // ======== JSON 解析（保持兼容 LLM 输出格式）========

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

    /** 从 JsonSchemaElement 的类名推断类型名称 */
    private String schemaType(JsonSchemaElement prop) {
        String className = prop.getClass().getSimpleName();
        return className
                .replace("Json", "").replace("Schema", "").toLowerCase();
    }

    /** 从 Command.update() 中提取工具执行结果 */
    private String extractToolResult(Command command) {
        if (command == null || command.update() == null) {
            return "工具执行完成";
        }
        Object val = command.update().get("toolResults");
        if (val instanceof java.util.List<?> list && !list.isEmpty()) {
            return list.get(list.size() - 1).toString();
        }
        return "工具执行完成";
    }
}
