package com.hmdp.agent;

import com.hmdp.config.TraceFilter;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LLM 调用级 trace —— 全局监听每一次模型请求/响应（planner/agent/reflection/answer 都覆盖），
 * 异步写入 agent_llm_trace 表，供 AgentTrace 会话时间线回放定位根因。
 *
 * <p>在 {@code OpenAiChatModel} 上注册（AgentConfig）。阶段推断：带 toolSpecifications → agent；
 * 带 responseFormat(JSON) → planner/reflection 等结构化输出；否则 other。
 */
@Component
public class LlmTraceListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LlmTraceListener.class);

    private static final Object REQ_TIME_KEY = new Object();

    /** prompt/response 完整入库（TEXT 列 64KB），供 AgentTrace 单条点击查看完整内容；
     *  仅防御性兜底：极端超大（>60KB）截断，避免 MySQL TEXT 溢出。 */
    private static final int MAX_TEXT = 60000;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "llm-trace-writer");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void init() {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put(REQ_TIME_KEY, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        try {
            long start = ctx.attributes().get(REQ_TIME_KEY) instanceof Long l ? l : 0L;
            long ms = start > 0 ? (System.nanoTime() - start) / 1_000_000 : 0L;
            ChatRequest req = ctx.chatRequest();
            ChatResponse resp = ctx.chatResponse();

            String traceId = MDC.get(TraceFilter.TRACE_ID_KEY);
            String stage = inferStage(req);
            String prompt = renderMessages(req.messages());
            String response = resp.aiMessage() != null
                    ? renderAi(resp.aiMessage()) : "(无响应)";
            Integer in = resp.tokenUsage() != null ? resp.tokenUsage().inputTokenCount() : null;
            Integer out = resp.tokenUsage() != null ? resp.tokenUsage().outputTokenCount() : null;
            String model = req.modelName();

            final String fTraceId = traceId;
            final long fMs = ms;
            executor.submit(() -> save(fTraceId, stage, prompt, response, in, out, (int) fMs, model));
        } catch (Exception e) {
            log.warn("LlmTraceListener onResponse failed: {}", e.getMessage());
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        String traceId = MDC.get(TraceFilter.TRACE_ID_KEY);
        final String t = traceId;
        executor.submit(() -> save(t, "error", null,
                "错误: " + (ctx.error() != null ? ctx.error().getMessage() : "unknown"), null, null, null, null));
    }

    private void save(String traceId, String stage, String prompt, String response,
                      Integer in, Integer out, Integer ms, String model) {
        try {
            jdbc.update("INSERT INTO agent_llm_trace (trace_id, stage, prompt, response, input_tokens, output_tokens, duration_ms, model) " +
                            "VALUES (?,?,?,?,?,?,?,?)",
                    traceId, stage, prompt, response, in, out, ms, model);
        } catch (Exception e) {
            log.warn("agent_llm_trace 写库失败（表可能未创建）: {}", e.getMessage());
        }
    }

    private String inferStage(ChatRequest req) {
        if (req.toolSpecifications() != null && !req.toolSpecifications().isEmpty()) return "agent";
        if (req.responseFormat() != null) return "structured";
        return "other";
    }

    /**
     * 完整渲染请求消息列表（每条消息全量、不截断），
     * 供 AgentTrace 前端点击单条展开查看完整 prompt（含规则段/skill 规则/工具结果折叠文本等）。
     */
    private String renderMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "(空)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            String content = messageText(m);
            if (content == null) content = m.type().name();
            sb.append("[").append(i + 1).append("/").append(messages.size()).append(" ")
              .append(m.type()).append("] ").append(content).append("\n");
        }
        String s = sb.toString();
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) + "…（超长截断）" : s;
    }

    /** 各消息子类的文本提取（ChatMessage 接口本身无 text()）。 */
    private String messageText(ChatMessage m) {
        if (m instanceof dev.langchain4j.data.message.AiMessage ai) {
            return ai.toolExecutionRequests() != null && !ai.toolExecutionRequests().isEmpty()
                    ? "(工具调用)" : ai.text();
        }
        if (m instanceof dev.langchain4j.data.message.UserMessage u) return u.singleText();
        if (m instanceof dev.langchain4j.data.message.SystemMessage s) return s.text();
        if (m instanceof dev.langchain4j.data.message.ToolExecutionResultMessage t) return t.text();
        return null;
    }

    /** 完整响应文本（不截断；工具调用场景列出工具名 + 完整参数 JSON）。 */
    private String renderAi(dev.langchain4j.data.message.AiMessage ai) {
        if (ai.toolExecutionRequests() != null && !ai.toolExecutionRequests().isEmpty()) {
            StringBuilder sb = new StringBuilder("工具调用:\n");
            for (dev.langchain4j.agent.tool.ToolExecutionRequest r : ai.toolExecutionRequests()) {
                sb.append("- ").append(r.name()).append("(").append(r.arguments()).append(")\n");
            }
            return sb.toString();
        }
        String t = ai.text() != null ? ai.text() : "";
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) + "…（超长截断）" : t;
    }
}
