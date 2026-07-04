package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.ToolContext;
import com.hmdp.agent.graph.error.ErrorCategory;
import com.hmdp.agent.graph.error.ErrorClassifier;
import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
        sb.append("- 当前步骤必须是可执行的工具调用。如果步骤内容是「告知用户」「让用户选择」「提示用户」等非工具操作，输出 ask_user 把消息传给用户，不要自行调用工具\n");
        sb.append("- 如果上一步结果摘要中有 [自动重试] 提示，说明上次查询返回空：必须扩大搜索范围（查更多表、用更宽松匹配），不要用完全相同的参数重试\n");
        sb.append("- 如果 [自动重试 2/2] 后仍为空，输出 ask_user 如实告知用户未找到数据，不要继续重试\n");
        sb.append("- [必填] 参数必须全部提供，缺一不可！如果缺少必填参数，必须输出 ask_user\n");
        sb.append("- 禁止编造参数值！如果用户没有提供某个必填参数的值，且之前的工具结果中也找不到，必须输出 ask_user\n");
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
        String plan = state.remainPlan() != null && !state.remainPlan().isEmpty()
                ? state.remainPlan() : "{}";

        StringBuilder prompt = new StringBuilder();
        prompt.append("选择一个工具执行。严格遵循计划。只输出JSON: {\"tool\": \"工具名\", \"args\": {...}}\n\n");

        prompt.append("用户: ").append(query).append("\n");
        prompt.append("待执行步骤: ").append(plan).append("\n");
        String obs = state.observerReport();
        if (obs != null && !obs.isEmpty()) {
            prompt.append("上一步结果摘要: ").append(obs).append("\n");
        }
        prompt.append("工具结果: ").append(PlannerNode.formatToolResults(scratchpad)).append("\n\n");
        prompt.append(toolSchemaPrompt);
        log.info("tool prompt:{}", prompt);

        // Step 1 — LLM 选工具
        String raw;
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是工具调度器。严格按计划执行。必填参数缺一不可，禁止编造值（如坐标填0）。只输出JSON。"),
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

        // Step 3 — 执行工具（设置 ToolContext 让工具跨线程读取 userId）
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(argsStr)
                .build();

        Long uid = state.userId();
        log.info("ExecutorNode: setting userId={} into ToolContext for tool {}", uid, toolName);
        ToolContext.setUserId(uid);
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
                scratchpad.put("_last_args", argsStr);
                scratchpad.put("_last_result", result);
                String truncated = result.length() > 200 ? result.substring(0, 200) + "..." : result;
                log.info("Tool {} returned success, stored as {}: {}", toolName, key, truncated);
                return Map.of("scratchpad", scratchpad,
                        "retryCount", 0,
                        "emptyResultRetries", 0,
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
        } finally {
            ToolContext.clear();
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

        Long uid = state.userId();
        log.info("ExecutorNode(retry): setting userId={} into ToolContext for tool {}", uid, toolName);
        ToolContext.setUserId(uid);
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
                scratchpad.put("_last_args", argsStr);
                scratchpad.put("_last_result", result);
                log.info("Retry succeeded for tool {}, stored as {}: {}",
                        toolName, key, result.length() > 200 ? result.substring(0, 200) + "..." : result);
                return Map.of("scratchpad", scratchpad,
                        "retryCount", 0,
                        "emptyResultRetries", 0,
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
        } finally {
            ToolContext.clear();
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
                String ctx = buildErrorContext(state, toolName, errorDetail, "USER_FIXABLE");
                scratchpad.put("ask_user_missing", errorDetail);
                yield Map.of(
                        "scratchpad", scratchpad,
                        "errorCategory", ErrorCategory.USER_FIXABLE.name(),
                        "errorContext", ctx,
                        "retryCount", 0,
                        "lastToolName", "",
                        "lastToolArgs", "",
                        "finalAnswer", "__ERROR__",
                        "nextNode", "answer"
                );
            }
            case FATAL -> {
                log.warn("Error categorized as FATAL for tool {}", toolName);
                int fatals = state.fatalErrorCount() + 1;
                String ctx = buildErrorContext(state, toolName, errorDetail, "FATAL");
                yield Map.of(
                        "scratchpad", scratchpad,
                        "errorCategory", ErrorCategory.FATAL.name(),
                        "errorContext", ctx,
                        "retryCount", 0,
                        "lastToolName", "",
                        "lastToolArgs", "",
                        "fatalErrorCount", fatals,
                        "finalAnswer", "__ERROR__",
                        "nextNode", "answer"
                );
            }
        };
    }

    // ============================================================
    // 错误上下文生成 — 交给 AnswerNode 做用户友好的脱敏输出
    // ============================================================
    private String buildErrorContext(ReActAgentState state, String toolName,
                                      String errorDetail, String category) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("【错误类型】").append(category).append("\n");
        ctx.append("【用户问题】").append(state.userQuery()).append("\n");
        ctx.append("【当前计划】").append(state.remainPlan() != null && !state.remainPlan().isEmpty()
                ? state.remainPlan() : "无").append("\n");
        ctx.append("【出错工具】").append(toolName).append("\n");
        // 脱敏处理：截断过长错误，去掉 SQL 等敏感信息
        String safe = errorDetail != null ? errorDetail : "未知错误";
        if (safe.length() > 500) safe = safe.substring(0, 500) + "...";
        safe = safe.replaceAll("(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\\s+.*", "[SQL已脱敏]");
        ctx.append("【错误详情】").append(safe).append("\n");
        return ctx.toString();
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

    /**
     * 从 Command 中提取纯净的工具执行结果文本。
     * 避免 ToolExecutionResultMessage 包装类的 toString（含 id、contents 等噪音）
     * 污染 scratchpad，导致后续 LLM 调用时关键字段被噪声淹没。
     */
    private String extractToolResult(Command command) {
        if (command == null || command.update() == null) {
            return "工具执行完成";
        }
        Object val = command.update().get("toolResults");
        if (val instanceof java.util.List<?> list && !list.isEmpty()) {
            Object last = list.get(list.size() - 1);
            if (last instanceof ToolExecutionResultMessage msg) {
                return msg.contents().stream()
                        .filter(c -> c instanceof TextContent)
                        .map(c -> ((TextContent) c).text())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("工具执行完成");
            }
            return last.toString();
        }
        return "工具执行完成";
    }
}
