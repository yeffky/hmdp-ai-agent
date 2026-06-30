package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.error.ErrorCategory;
import com.hmdp.agent.graph.error.ErrorClassifier;
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
 * Executor Node — LLM 选工具 → LC4jToolService 执行 → 结果写入 scratchpad。
 *
 * <h3>错误分类路由（参照 LangChain4j ReturnBehavior + LangGraph errorHandler）</h3>
 * <ul>
 *   <li><b>RETRYABLE</b>（网络超时/Redis 不可用）→ retryGate → executor 重试</li>
 *   <li><b>USER_FIXABLE</b>（缺参数/需登录）→ answer，暂停等用户补充</li>
 *   <li><b>FATAL</b>（SQL 错误/schema 不匹配）→ answer，优雅降级</li>
 * </ul>
 */
public class ExecutorNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ExecutorNode.class);
    private final OpenAiChatModel model;
    private final LC4jToolService toolService;
    private final String toolSchemaPrompt;
    private final boolean llmErrorClassify;

    public ExecutorNode(OpenAiChatModel model, LC4jToolService toolService, boolean llmErrorClassify) {
        this.model = model;
        this.toolService = toolService;
        this.llmErrorClassify = llmErrorClassify;
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
        Map<String, Object> scratchpad = state.scratchpad();

        // ============================================================
        // 重试模式：跳过 LLM 选工具，直接用上次的工具+参数重试
        // ============================================================
        if (ErrorCategory.RETRYABLE.name().equals(state.errorCategory())
                && state.lastToolName() != null && !state.lastToolName().isEmpty()) {
            return executeRetry(state);
        }

        // ============================================================
        // 正常模式：LLM 选择工具 → 执行 → 分类结果
        // ============================================================
        String plan = state.planJson() != null ? state.planJson() : "{}";

        StringBuilder prompt = new StringBuilder();
        prompt.append("选择一个工具执行。只输出JSON: {\"tool\": \"工具名\", \"args\": {...}}\n\n");

        prompt.append(state.contextBlock()).append("\n");

        prompt.append("用户: ").append(query).append("\n");
        prompt.append("计划: ").append(plan).append("\n");
        prompt.append("工具结果: ").append(PlannerNode.formatToolResults(scratchpad)).append("\n\n");
        prompt.append(toolSchemaPrompt);

        // Step 1 — LLM 选工具
        String raw;
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是工具调度器。只输出JSON。"),
                    UserMessage.from(prompt.toString())));
            raw = resp.aiMessage().text().trim();
            log.info("Executor call: {}", raw);
        } catch (Exception e) {
            log.error("Executor LLM call failed", e);
            return handleError(state, ErrorClassifier.classifyException(e),
                    "_llm_", "{}", "LLM调用异常: " + e.getMessage(), scratchpad);
        }

        String toolName = extractStr(raw, "tool");
        String argsStr = extractObj(raw, "args");

        // Step 2 — ask_user 检测
        if (toolName.isEmpty() || toolName.equals("ask_user")) {
            String missing = extractStr(raw, "missing");
            String question = (missing != null && !missing.isEmpty())
                    ? "请提供以下信息：" + missing
                    : "请提供更多信息以便为您查询";
            scratchpad.put("ask_user_missing", question);
            log.info("Executor asks user: {}", question);
            return Map.of("scratchpad", scratchpad,
                    "errorCategory", ErrorCategory.USER_FIXABLE.name(),
                    "retryCount", 0,
                    "lastToolName", "",
                    "lastToolArgs", "",
                    "finalAnswer", question,
                    "nextNode", "answer");
        }

        // Step 3 — 执行工具
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(argsStr)
                .build();

        try {
            var execResult = toolService.execute(
                    List.of(request),
                    InvocationContext.builder().build(),
                    "toolResults"
            ).get();

            String result = extractToolResult(execResult);

            // Step 4 — 分类工具返回的字符串结果
            ErrorCategory cat = ErrorClassifier.classifyToolResult(result);
            if (cat == null) {
                // 正常成功
                String key = "result_" + (scratchpad.size() + 1);
                scratchpad.put(key, result);
                scratchpad.put("_last_tool", toolName);
                scratchpad.put("_last_result", result);
                String truncated = result.length() > 200 ? result.substring(0, 200) + "..." : result;
                log.info("Tool {} returned success, stored as {}: {}", toolName, key, truncated);
                return Map.of("scratchpad", scratchpad,
                        "retryCount", 0,
                        "errorCategory", "",
                        "lastToolName", "",
                        "lastToolArgs", "",
                        "nextNode", "observer");
            }

            // 工具返回了错误字符串
            log.warn("Tool {} returned error, category={}: {}",
                    toolName, cat, result.length() > 120 ? result.substring(0, 120) : result);
            scratchpad.put("error", result);
            return handleError(state, cat, toolName, argsStr, result, scratchpad);

        } catch (Exception e) {
            log.error("Tool execution threw exception for {}", toolName, e);
            String errDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            scratchpad.put("error", errDetail);
            return handleError(state, ErrorClassifier.classifyException(e),
                    toolName, argsStr, errDetail, scratchpad);
        }
    }

    // ============================================================
    // 重试模式：跳过 LLM，直接用上次的工具名和参数执行
    // ============================================================
    private Map<String, Object> executeRetry(ReActAgentState state) throws Exception {
        String toolName = state.lastToolName();
        String argsStr = state.lastToolArgs();
        Map<String, Object> scratchpad = state.scratchpad();

        log.info("Retry mode: re-executing tool {} with args {}", toolName, argsStr);

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(argsStr)
                .build();

        try {
            var execResult = toolService.execute(
                    List.of(request),
                    InvocationContext.builder().build(),
                    "toolResults"
            ).get();

            String result = extractToolResult(execResult);
            ErrorCategory cat = ErrorClassifier.classifyToolResult(result);

            if (cat == null) {
                // 重试成功
                String key = "result_" + (scratchpad.size() + 1);
                scratchpad.put(key, result);
                scratchpad.put("_last_tool", toolName);
                scratchpad.put("_last_result", result);
                log.info("Retry succeeded for tool {}, stored as {}: {}",
                        toolName, key, result.length() > 200 ? result.substring(0, 200) + "..." : result);
                return Map.of("scratchpad", scratchpad,
                        "retryCount", 0,
                        "errorCategory", "",
                        "lastToolName", "",
                        "lastToolArgs", "",
                        "nextNode", "observer");
            }

            // 重试后依然失败 → 重新分类（可能转为 FATAL）
            log.warn("Retry still failed for tool {}, category={}", toolName, cat);
            scratchpad.put("error", result);
            return handleError(state, cat, toolName, argsStr, result, scratchpad);

        } catch (Exception e) {
            log.error("Retry threw exception for {}", toolName, e);
            String errDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            scratchpad.put("error", errDetail);
            // 重试过程中抛异常 → 重新分类
            ErrorCategory cat = ErrorClassifier.classifyException(e);
            // 如果还是 RETRYABLE，让 retryGate 处理（继续重试或耗尽）
            return handleError(state, cat, toolName, argsStr, errDetail, scratchpad);
        }
    }

    // ============================================================
    // LLM 辅助错误分类：regex 不确定时调用 LLM 进行语义判断
    // ============================================================
    private ErrorCategory classifyWithLLM(String toolName, String toolArgs, String errorDetail) {
        String prompt = String.format("""
                分类以下工具调用的错误：

                工具：%s
                参数：%s
                错误：%s

                类别定义：
                - RETRYABLE: 网络超时、连接断开、服务暂时不可用、限流等可自动重试的瞬态故障
                - USER_FIXABLE: 缺少参数、参数格式错误、需要登录、权限不足等用户可修正的问题
                - FATAL: SQL错误、数据损坏、认证失败、系统崩溃等不可恢复的问题

                只输出一个单词：RETRYABLE / USER_FIXABLE / FATAL""",
                toolName, toolArgs,
                errorDetail.length() > 300 ? errorDetail.substring(0, 300) : errorDetail);
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是错误分类器，只输出一个单词。"),
                    UserMessage.from(prompt)));
            String raw = resp.aiMessage().text().trim().toUpperCase();
            log.info("LLM error classify for {}: {} -> {}", toolName, errorDetail.length() > 80
                    ? errorDetail.substring(0, 80) + "..." : errorDetail, raw);
            if (raw.contains("RETRYABLE")) return ErrorCategory.RETRYABLE;
            if (raw.contains("USER_FIXABLE") || raw.contains("USER")) return ErrorCategory.USER_FIXABLE;
            return ErrorCategory.FATAL;
        } catch (Exception e) {
            log.warn("LLM error classification failed, keeping original category", e);
            return null;
        }
    }

    // ============================================================
    // 统一错误路由
    // ============================================================
    private Map<String, Object> handleError(
            ReActAgentState state, ErrorCategory cat,
            String toolName, String toolArgs, String errorDetail,
            Map<String, Object> scratchpad) {

        // 两层分类：regex 返回 FATAL（catch-all）→ 尝试 LLM 重新判断
        if (cat == ErrorCategory.FATAL && llmErrorClassify) {
            ErrorCategory llmCat = classifyWithLLM(toolName, toolArgs, errorDetail);
            if (llmCat != null && llmCat != ErrorCategory.FATAL) {
                log.info("LLM reclassified error from FATAL to {} for tool {}", llmCat, toolName);
                cat = llmCat;
            }
        }

        return switch (cat) {
            case RETRYABLE -> {
                log.info("Error categorized as RETRYABLE for tool {}, will route to retryGate", toolName);
                int retries = state.retryCount();
                yield Map.of(
                        "scratchpad", scratchpad,
                        "errorCategory", ErrorCategory.RETRYABLE.name(),
                        "lastToolName", toolName,
                        "lastToolArgs", toolArgs,
                        "nextNode", "retryGate"
                );
            }
            case USER_FIXABLE -> {
                log.info("Error categorized as USER_FIXABLE for tool {}", toolName);
                String question = buildUserFixableMessage(toolName, errorDetail);
                scratchpad.put("ask_user_missing", question);
                yield Map.of(
                        "scratchpad", scratchpad,
                        "errorCategory", ErrorCategory.USER_FIXABLE.name(),
                        "retryCount", 0,
                        "lastToolName", "",
                        "lastToolArgs", "",
                        "finalAnswer", question,
                        "nextNode", "answer"
                );
            }
            case FATAL -> {
                log.warn("Error categorized as FATAL for tool {}", toolName);
                int fatals = state.fatalErrorCount() + 1;
                String msg = buildFatalMessage(toolName, errorDetail);
                yield Map.of(
                        "scratchpad", scratchpad,
                        "errorCategory", ErrorCategory.FATAL.name(),
                        "retryCount", 0,
                        "lastToolName", "",
                        "lastToolArgs", "",
                        "fatalErrorCount", fatals,
                        "finalAnswer", msg,
                        "nextNode", "answer"
                );
            }
        };
    }

    // ============================================================
    // 错误消息生成
    // ============================================================
    private String buildUserFixableMessage(String toolName, String errorDetail) {
        String missingParam = extractMissingParam(errorDetail);
        if (missingParam != null) {
            return "请提供以下信息：" + missingParam;
        }
        return "请提供更多信息以便为您查询";
    }

    private String buildFatalMessage(String toolName, String errorDetail) {
        if (errorDetail.contains("未登录") || errorDetail.contains("登录")) {
            return "您当前未登录，请先登录后再查询。";
        }
        if (errorDetail.contains("不存在") || errorDetail.contains("not found")) {
            return "未找到相关信息，请检查查询条件。";
        }
        return "抱歉，查询服务暂时不可用，请稍后重试。";
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

    private String schemaType(JsonSchemaElement prop) {
        String className = prop.getClass().getSimpleName();
        return className.replace("Json", "").replace("Schema", "").toLowerCase();
    }

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
