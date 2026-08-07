package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.ToolContext;
import com.hmdp.agent.graph.dto.JsonParser;
import com.hmdp.agent.graph.dto.ToolCallRequest;
import com.hmdp.agent.graph.error.ErrorCategory;
import com.hmdp.agent.graph.error.ErrorClassifier;
import com.hmdp.agent.graph.prompt.PromptTemplates;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.agent.tool.ShopTypeProvider;
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

    public ExecutorNode(OpenAiChatModel model, LC4jToolService toolService,
                        boolean llmErrorClassify, ShopTypeProvider shopTypeProvider) {
        this.model = model;
        this.toolService = toolService;
        this.llmErrorClassify = llmErrorClassify;
        this.toolSchemaPrompt = buildToolSchemaPrompt(shopTypeProvider);
    }

    /** 从 ToolSpecification 列表自动生成 prompt 中的工具描述 */
    private String buildToolSchemaPrompt(ShopTypeProvider typeProvider) {
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
        sb.append("- 如果上一步结果摘要中有 [自动重试] 提示，说明上次查询返回空：必须换一个查询思路（查不同表、用更短关键词、联表查询），禁止用完全相同的参数重试，也禁止输出 ask_user\n");
        sb.append("- 如果必填参数缺失且处于 [自动重试] 中，必须先通过扩大搜索来尝试获取参数，不得直接 ask_user\n");
        sb.append("- [必填] 参数必须全部提供，缺一不可！如果没有 [自动重试] 提示且缺少必填参数，输出 ask_user\n");
        sb.append("- 禁止编造参数值！如果用户没有提供某个必填参数的值，且之前的工具结果中也找不到，必须输出 ask_user\n");
        sb.append("- 商家类型映射（共").append(typeProvider.typeMap().size()).append("类：")
          .append(typeProvider.typeText()).append("）。用户说的具体菜系（茶餐厅/火锅/日料/烧烤等）统统归入美食，不要用菜系名去搜类型表\n");
        sb.append("- 写操作确认规则：如果要调用排队取号(takeQueueNumber)、取消排队(cancelMyQueue) 等写操作工具，且用户没有明确指定操作目标（如具体商铺名或ID），必须输出 ask_user 让用户选择确认，禁止自行从多个结果中挑选一个来执行。\n");
        sb.append("- 如果 [自动重试 2/2] 后仍为空，输出 ask_user，missing 写对用户说的话（如\"抱歉，未找到相关信息\"）\n");
        sb.append("- 当前步骤如果是「告知用户」「让用户选择」等非工具操作，输出 ask_user\n");
        sb.append("- missing 字段是直接展示给用户的文本，禁止写入内部指令（如\"告知用户\"\"如实反馈\"等），只写用户应看到的内容\n");
        sb.append("- 参数不足时输出 {\"ask_user\": true, \"missing\": \"用友好语言告知用户缺少什么\"}\n");
        sb.append("- 信息足够时输出 {\"tool\": \"工具名\", \"args\": {...}}\n");
        return sb.toString();
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
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
        String obs = state.observerReport();
        boolean hasObs = obs != null && !obs.isEmpty();
        boolean isReplan = !hasObs && (plan.contains("\"plan\"") || plan.contains("\"intent\""));
        // replan: Planner 刚重规划并清空了 observerReport → 鼓励执行新计划
        // retry: observerReport 含 [自动重试] → 工具规则里已要求扩大搜索，此处不加多余提示
        // normal: 有 observerReport 不含 retry → 多步计划的后续步骤

        if (isReplan) {
            prompt.append("Planner 已重新制定了计划。请执行当前步骤。\n\n");
        }
        prompt.append("选择一个工具执行。只输出JSON: {\"tool\": \"工具名\", \"args\": {...}}\n\n");

        prompt.append("待执行步骤: ").append(plan).append("\n");
        if (hasObs) {
            prompt.append("上一步结果摘要: ").append(obs).append("\n");
        }
        prompt.append("\n");
        prompt.append(toolSchemaPrompt);
        log.info("tool prompt:{}", prompt);

        // Step 1 — LLM 选工具
        String raw;
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from(PromptTemplates.EXECUTOR_SYSTEM),
                    UserMessage.from(prompt.toString())));
            raw = resp.aiMessage().text().trim();
            log.info("Executor call: {}", raw);
        } catch (Exception e) {
            log.error("Executor LLM call failed", e);
            return handleError(state, ErrorClassifier.classifyException(e),
                    "_llm_", "{}", "LLM调用异常: " + e.getMessage(), scratchpad);
        }

        // Step 1b — 结构化解析 LLM 输出（Jackson），替代手写抠 JSON
        ToolCallRequest call = JsonParser.parseToolCall(raw);

        // Step 2 — ask_user 检测：解析失败或 ask_user 均走 checkpoint 确认流程
        if (call == null || call.isAskUserRequest()) {
            String missing = call != null ? call.getMissing() : null;
            String question = (missing != null && !missing.isEmpty())
                    ? missing
                    : "请提供更多信息以便为您查询";

            log.info("Executor: ask_user, saving checkpoint confirmation — {}", question);
            return Map.of("scratchpad", scratchpad,
                    "pendingConfirmation", true,
                    "confirmationPrompt", question,
                    "finalAnswer", question,
                    "nextNode", "answer");
        }

        String toolName = call.getTool().trim();
        String argsStr = JsonParser.toJson(call.getArgs());

        // Step 3 — 执行工具（设置 ToolContext 让工具跨线程读取 userId）
        return executeTool(state, toolName, argsStr);
    }

    // ============================================================
    // 重试模式：跳过 LLM，直接用上次的工具名和参数执行
    // ============================================================
    private Map<String, Object> executeRetry(ReActAgentState state) throws Exception {
        String toolName = state.lastToolName();
        String argsStr = state.lastToolArgs();
        log.info("Retry mode: re-executing tool {} with args {}", toolName, argsStr);
        return executeTool(state, toolName, argsStr);
    }

    /**
     * 执行工具并把结果写入 scratchpad；异常统一走 handleError 分类路由。
     * 正常模式与重试模式共用，避免重复。
     */
    private Map<String, Object> executeTool(ReActAgentState state, String toolName, String argsStr) throws Exception {
        Map<String, Object> scratchpad = state.scratchpad();

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

            // 工具正常返回字符串 = 业务成功，写入 scratchpad
            String key = StateKeys.SP_RESULT_PREFIX + (scratchpad.size() + 1);
            scratchpad.put(key, result);
            scratchpad.put(StateKeys.SP_LAST_TOOL, toolName);
            scratchpad.put(StateKeys.SP_LAST_ARGS, argsStr);
            scratchpad.put(StateKeys.SP_LAST_RESULT, result);
            String truncated = result.length() > 200 ? result.substring(0, 200) + "..." : result;
            log.info("Tool {} completed, stored as {}: {}", toolName, key, truncated);
            int newCallCount = state.toolCallCount() + 1;
            scratchpad.put(StateKeys.SP_TOOL_CALL_COUNT_BEFORE, state.toolCallCount());
            return Map.of("scratchpad", scratchpad,
                    "toolCallCount", newCallCount,
                    "retryCount", 0,
                    "errorCategory", "",
                    "lastToolName", "",
                    "lastToolArgs", "",
                    "nextNode", "observer");

        } catch (Exception e) {
            log.error("Tool {} threw exception", toolName, e);
            String errDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            scratchpad.put(StateKeys.SP_ERROR, errDetail);
            return handleError(state, ErrorClassifier.classifyException(e),
                    toolName, argsStr, errDetail, scratchpad);
        } finally {
            ToolContext.clear();
        }
    }

    // ============================================================
    // LLM 辅助错误分类：regex 不确定时调用 LLM 进行语义判断
    // ============================================================
    private ErrorCategory classifyWithLLM(String toolName, String toolArgs, String errorDetail) {
        String prompt = String.format(PromptTemplates.ERROR_CLASSIFIER_PROMPT,
                toolName, toolArgs,
                errorDetail.length() > 300 ? errorDetail.substring(0, 300) : errorDetail);
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from(PromptTemplates.ERROR_CLASSIFIER_SYSTEM),
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
                log.info("Error categorized as USER_FIXABLE for tool {}, routing to judgeNode", toolName);
                String ctx = buildErrorContext(state, toolName, errorDetail, "USER_FIXABLE");
                scratchpad.put(StateKeys.SP_ASK_USER_MISSING, errorDetail);
                yield Map.of(
                        "scratchpad", scratchpad,
                        "errorCategory", ErrorCategory.USER_FIXABLE.name(),
                        "errorContext", ctx,
                        "retryCount", 0,
                        "lastToolName", "",
                        "lastToolArgs", "",
                        "nextNode", "judgeNode"
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

    private String schemaType(JsonSchemaElement prop) {
        String className = prop.getClass().getSimpleName();
        return className.replace("Json", "").replace("Schema", "").toLowerCase();
    }

    /**
     * 从 Command 中提取工具执行结果文本。
     * 如果 langchain4j 捕获了工具抛出的异常并将其包装为 error content，则重新抛出。
     */
    private String extractToolResult(Command command) {
        if (command == null || command.update() == null) {
            return "工具执行完成";
        }
        Object val = command.update().get("toolResults");
        if (val instanceof java.util.List<?> list && !list.isEmpty()) {
            Object last = list.get(list.size() - 1);
            if (last instanceof ToolExecutionResultMessage msg) {
                // 检测 langchain4j 包装的工具异常，重新抛出以触发 ExecutorNode 的错误路由
                if (Boolean.TRUE.equals(msg.isError())) {
                    throw new com.hmdp.agent.graph.error.ToolException(
                            msg.toolName() != null ? msg.toolName() : "unknown",
                            msg.text());
                }
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
