package com.hmdp.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.AgentTracer;
import com.hmdp.agent.StreamingResumeDedupe;
import com.hmdp.agent.graph.GraphInputFactory;
import com.hmdp.agent.graph.NodeNames;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.config.TraceFilter;
import com.hmdp.repository.ChatHistoryRepository;
import com.hmdp.utils.IdObfuscator;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.PromptSanitizer;
import com.hmdp.utils.UserResolver;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.async.AsyncGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/chat")
public class ReactStreamController {

    private static final Logger log = LoggerFactory.getLogger(ReactStreamController.class);

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @Resource
    private OpenAiStreamingChatModel streamingModel;

    @Resource
    private ChatHistoryRepository chatHistoryRepo;

    @Resource
    private AgentTracer agentTracer;

    @Resource
    private com.hmdp.agent.memory.reflection.ReflectionRecorder reflectionRecorder;

    @Resource
    private com.hmdp.agent.memory.context.AnswerInjection answerInjection;

    @Resource(name = "agentExecutor")
    private java.util.concurrent.ExecutorService agentExecutor;

    @Resource(name = "agentRetryScheduler")
    private java.util.concurrent.ScheduledExecutorService agentRetryScheduler;

    @Resource
    private javax.sql.DataSource dataSource;

    @Resource
    private IdObfuscator idObfuscator;

    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @javax.annotation.PostConstruct
    void initJdbc() {
        this.jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    @Resource
    private JwtUtil jwtUtil;

    @PostMapping(value = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamReact(@RequestBody Map<String, String> request,
                                  HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(300_000L);

        // 入口清洗：去控制字符 + 截断，防 prompt 注入
        String message = PromptSanitizer.sanitize(request.get("message"));
        // 用户当前定位（前端地区中心坐标），注入 Agent 上下文供 geoSearch 等直接用
        String centerX = request.get("centerX");
        String centerY = request.get("centerY");
        String districtIdStr = request.get("districtId"); // 用户当前地区 id（1拱墅/2鼓楼）
        if (message.isEmpty()) {
            completeWithError(emitter, "消息不能为空");
            return emitter;
        }

        Long userId = UserResolver.resolveUserId(httpRequest, jwtUtil);
        log.info("ReactStreamController: resolved userId={}", userId);

        emitter.onTimeout(() -> log.warn("SSE timeout after 300s for userId={}", userId));
        emitter.onError(e -> log.warn("SSE error for userId={}: {}", userId, e.getMessage()));
        emitter.onCompletion(() -> log.debug("SSE completed for userId={}", userId));

        // 立即发送 SSE comment 事件，冲开代理/容器的 response buffer，
        // 确保后续 token 实时到达前端，避免被缓冲为一整块
        String traceId = MDC.get(TraceFilter.TRACE_ID_KEY);
        CompletableFuture.runAsync(() -> withTrace(traceId, () -> {
            try {
                emitter.send(SseEmitter.event().comment("connected"));
                log.debug("SSE heartbeat sent, connection established");
            } catch (IOException e) {
                log.debug("SSE heartbeat failed: {}", e.getMessage());
            }
        }), agentExecutor).thenRunAsync(() -> withTrace(traceId, () -> {
            try {
                if (userId == null || userId <= 0) {
                    completeWithError(emitter, "请先登录");
                    return;
                }

                String threadId = "user:" + userId;
                RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

                // ============================================================
                // 确认恢复：检测是否有待确认的 checkpoint，有则将用户回复注入并跳到 agent 恢复决策
                // ============================================================
                boolean resumed = false;
                try {
                    var snapshotOpt = reactGraph.stateOf(config);
                    if (snapshotOpt.isPresent()) {
                        ReActAgentState lastState = snapshotOpt.get().state();
                        if (lastState.pendingConfirmation()) {
                            log.info("Resuming from confirmation checkpoint: threadId={}, userChoice={}",
                                    threadId, message);
                            reactGraph.updateState(config, Map.of(
                                    StateKeys.USER_CHOICE, message,
                                    StateKeys.PENDING_CONFIRMATION, false,
                                    StateKeys.NEXT_NODE, NodeNames.AGENT,
                                    StateKeys.FINAL_ANSWER, ""   // 清掉 ask_user 残留，避免数据足够后仍回复旧问题
                            ), NodeNames.AGENT);
                            resumed = true;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to check/resume confirmation checkpoint for {}: {}",
                            threadId, e.getMessage());
                }

                Map<String, Object> init;
                if (resumed) {
                    init = null;  // null → GraphInput.resume() → resume from checkpoint
                } else {
                    init = GraphInputFactory.newInit(threadId, message, userId, centerX, centerY, districtIdStr);
                }

                long t0 = System.currentTimeMillis();
                AsyncGenerator<NodeOutput<ReActAgentState>> stream =
                        reactGraph.stream(init, config);
                log.info("Graph stream init took {} ms for {}", System.currentTimeMillis() - t0, threadId);

                List<Map<String, Object>> steps = new ArrayList<>();
                ReActAgentState[] lastState = {null};

                try {
                    stream.iterator().forEachRemaining(output -> {
                        try {
                            lastState[0] = output.state();
                            String node = output.node();

                            if (NodeNames.CONTEXT.equals(node)) {
                                log.info("First node (context) reached after {} ms from stream init",
                                        System.currentTimeMillis() - t0);
                            }
                            Map<String, Object> event = new LinkedHashMap<>();
                            event.put("node", node);

                            switch (node) {
                                case NodeNames.CONTEXT:
                                    event.put("type", "thinking");
                                    event.put("content", "正在整理上下文...");
                                    break;
                                case NodeNames.PLANNER:
                                    event.put("type", "thinking");
                                    String plan = lastState[0].plan();
                                    event.put("content", planSummary(plan));
                                    event.put("plan", plan);
                                    break;
                                case NodeNames.AGENT:
                                    event.put("type", "thinking");
                                    event.put("content", "正在分析并执行下一步...");
                                    break;
                                case NodeNames.TOOLS:
                                    // 工具执行完成：发过程提示 + 结构化卡片（前端检测渲染，穿插在 SSE 流中）
                                    Map<String, Object> toolSp = lastState[0].scratchpad();
                                    String toolName = toolSp.get("_last_tool") != null
                                            ? toolSp.get("_last_tool").toString() : null;
                                    if (toolName != null) {
                                        String toolFull = toolSp.get("_last_result_full") != null
                                                ? toolSp.get("_last_result_full").toString() : null;
                                        event.put("type", "tool");
                                        event.put("toolName", toolName);
                                        event.put("content", "调用 " + toolName + " 获取信息");
                                        if (toolFull != null) {
                                            List<Map<String, Object>> cards = buildShopCards(toolName, toolFull);
                                            // 工具过程卡片：全量进前端「调用链」折叠展示（过程反馈），不作为最终回答卡片
                                            if (cards != null && !cards.isEmpty()) {
                                                event.put("cards", cards);
                                            }
                                        }
                                    } else {
                                        event.put("type", "thinking");
                                        event.put("content", "正在执行工具...");
                                    }
                                    break;
                                case NodeNames.ANSWER:
                                    String answer = lastState[0].finalAnswer();
                                    if (StateKeys.SENTINEL_STREAMING.equals(answer)) {
                                        event.put("type", "thinking");
                                        event.put("content", "正在生成回答...");
                                    } else {
                                        event.put("type", "thinking");
                                        event.put("content", "正在整理回答...");
                                    }
                                    break;
                            }

                            Map<String, Object> step = new LinkedHashMap<>();
                            step.put("node", node);
                            Object spTool = lastState[0].scratchpad().get("_last_tool");
                            if (spTool != null) {
                                step.put("tool", spTool.toString());
                                // 工具级 trace：参数 + 结果摘要（截断，避免超大）
                                Object spArgs = lastState[0].scratchpad().get("_last_args");
                                Object spResult = lastState[0].scratchpad().get("_last_result");
                                if (spArgs != null) {
                                    step.put("toolArgs", spArgs.toString().length() > 300
                                            ? spArgs.toString().substring(0, 300) + "..." : spArgs.toString());
                                }
                                if (spResult != null) {
                                    step.put("toolResult", spResult.toString().length() > 500
                                            ? spResult.toString().substring(0, 500) + "..." : spResult.toString());
                                }
                            }
                            step.put("at", System.currentTimeMillis() - t0);
                            steps.add(step);

                            emitter.send(SseEmitter.event()
                                    .name("step")
                                    .data(event));
                        } catch (Exception e) {
                            log.debug("SSE send failed: {}", e.getMessage());
                        }
                    });
                    agentTracer.record(traceId, userId, message, steps,
                            System.currentTimeMillis() - t0, "completed", null);
                } catch (Exception iterEx) {
                    log.warn("Graph iteration aborted, generating fallback: {}", iterEx.getMessage());
                    agentTracer.record(traceId, userId, message, steps,
                            System.currentTimeMillis() - t0, "error", iterEx.getMessage());
                    if (lastState[0] == null) {
                        try {
                            var snap = reactGraph.stateOf(config);
                            if (snap.isPresent()) lastState[0] = snap.get().state();
                        } catch (Exception ignored) {}
                    }
                    if (lastState[0] != null) {
                        String fallback = buildFallbackAnswer(lastState[0]);
                        for (int i = 0; i < fallback.length(); i++) {
                            emitter.send(SseEmitter.event().name("step").data(Map.of(
                                    "type", "answer_chunk", "content", String.valueOf(fallback.charAt(i)))));
                        }
                        Map<String, Object> fallbackEvent = new LinkedHashMap<>();
                        fallbackEvent.put("type", "answer");
                        fallbackEvent.put("content", fallback);
                        emitter.send(SseEmitter.event().name("step").data(fallbackEvent));
                        persistRound(userId, lastState[0].userQuery(), fallback, lastState[0], null);
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                        emitter.complete();
                        return;
                    }
                    throw iterEx;
                }

                // Reflexion 写路径：本次运行失败则异步沉淀一条教训（不阻塞流式回答）
                if (lastState[0] != null) {
                    reflectionRecorder.recordIfFailed(lastState[0]);
                }

                // 卡片不在此处发：由 doStreamingAnswer 流式回答中检测店名出现 → 实时发 card event（穿插）

                // 写操作确认：HITL 原地暂停——确认卡片 + 按钮已发，结束 SSE（用户回复后 resume 回 agent 执行）。
                boolean pwPending = lastState[0] != null
                        && lastState[0].pendingWrite() != null && !lastState[0].pendingWrite().isEmpty();
                if (pwPending) {
                    sendConfirmActions(emitter, lastState[0]);
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                    return;
                }

                // 普通回答 或 选项选择叙述：统一走流式（AnswerNode 对选项挂起已设 STREAMING 叙述 prompt）。
                // 叙述流式完成后由 onCompleteResponse 补发选择按钮（sendOptionsEvent），且不持久化——
                // 叙述只是选择前的介绍语，不是最终回答。最终卡片仍由流式回答中的 [[id]] 占位符触发。
                if (lastState[0] != null) {
                    doStreamingAnswer(lastState[0], emitter, userId, config);
                } else {
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("Streaming ReAct error", e);
                completeWithError(emitter, e.getMessage());
            }
        }), agentExecutor);

        return emitter;
    }

    /** 写操作确认：确认/取消按钮 + 写操作确认卡片（卡片 schema 由对应 skill 的 references 定义）。 */
    private void sendConfirmActions(SseEmitter emitter, ReActAgentState state) throws IOException {
        Map<String, Object> actionEvent = new LinkedHashMap<>();
        actionEvent.put("type", "actions");
        actionEvent.put("hint", state.confirmationPrompt());
        actionEvent.put("actions", List.of(
                Map.of("label", "确认", "value", "确认"),
                Map.of("label", "取消", "value", "取消")));
        String pw = state.pendingWrite();
        if (pw != null && !pw.isBlank()) {
            Map<String, Object> card = buildWriteConfirmCard(pw, state.userId());
            if (card != null) actionEvent.put("card", card);
        }
        emitter.send(SseEmitter.event().name("step").data(actionEvent));
    }

    /** 选项选择按钮：叙述流式完成后补发（hint 推荐语 + 选项按钮）；pendingOptions 为空则不发。 */
    private void sendOptionsEvent(SseEmitter emitter, ReActAgentState state) throws IOException {
        String po = state.pendingOptions();
        if (po == null || po.isEmpty()) return;
        List<Map<String, Object>> actions = parseOptions(po);
        if (actions.isEmpty()) return;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "actions");
        event.put("hint", state.confirmationPrompt());
        event.put("actions", actions);
        emitter.send(SseEmitter.event().name("step").data(event));
    }

    /** 在异步线程透传 traceId 到 MDC，使图节点日志可与请求链路按 traceId 串联 */
    private void withTrace(String traceId, Runnable task) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TraceFilter.TRACE_ID_KEY, traceId);
        }
        try {
            task.run();
        } finally {
            if (traceId != null && !traceId.isEmpty()) {
                MDC.remove(TraceFilter.TRACE_ID_KEY);
            }
        }
    }

    /**
     * True token-by-token streaming via OpenAiStreamingChatModel + StreamingChatResponseHandler。
     * 流式中断（Connection reset 等）时指数退避重连 + 断点续传：最多重试 {@code MAX_STREAM_ATTEMPTS} 次，
     * 每次把已生成的部分文本作为「续传锚点」重新喂给 LLM，前端已显示的内容不重复。
     */
    private static final int MAX_STREAM_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 500; // 指数退避基数：500ms → 1s → 2s

    private void doStreamingAnswer(ReActAgentState state, SseEmitter emitter, Long userId,
                                    RunnableConfig config) {
        // 消息注入（规则段 + 摘要 + 无历史上下文 + trimmed messages），工具数据来自标准 messages 通道
        startStreamAttempt(state, emitter, userId, config, 1, new StringBuilder(), new ArrayList<>());
    }

    /** 发起一次流式尝试。attempt>1 时把已生成的部分文本（partial）作为一条 UserMessage 追加（断点续传锚点）。 */
    private void startStreamAttempt(ReActAgentState state, SseEmitter emitter, Long userId,
                                    RunnableConfig config, int attempt,
                                    StringBuilder partial, List<Map<String, Object>> answerBlocks) {
        List<ChatMessage> msgs = answerInjection.build(state);
        if (attempt > 1 && partial.length() > 0) {
            // 断点续传：告诉 LLM 已生成的部分，让它从中断处继续，避免从头重复
            msgs.add(UserMessage.from("## 续传要求\n以下是已经生成的部分回答，请**从中断处继续**，"
                    + "不要重复这段内容，直接从断点之后继续输出：\n" + partial));
        }
        log.debug("Answer Injection: {} messages (roles={})", msgs.size(),
                msgs.stream().map(m -> String.valueOf(m.type())).collect(java.util.stream.Collectors.joining(",")));
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(msgs)
                .build();
        try {
            streamingModel.chat(chatRequest, ChatRequestOptions.EMPTY,
                    buildRetryableHandler(state, emitter, userId, config, attempt, partial, answerBlocks));
        } catch (Exception e) {
            log.warn("Streaming start failed (attempt {}/{}): {}", attempt, MAX_STREAM_ATTEMPTS, e.getMessage());
            scheduleRetry(state, emitter, userId, config, attempt, partial, answerBlocks);
        }
    }

    /** 指数退避后重试续传；已达上限则最终失败（入库失败记录 + 错误事件）。 */
    private void scheduleRetry(ReActAgentState state, SseEmitter emitter, Long userId,
                               RunnableConfig config, int attempt,
                               StringBuilder partial, List<Map<String, Object>> answerBlocks) {
        if (attempt >= MAX_STREAM_ATTEMPTS) {
            finalFailure(state, emitter, userId, config, partial, answerBlocks);
            return;
        }
        long delay = RETRY_BASE_DELAY_MS << (attempt - 1); // 指数退避：500ms → 1s → 2s
        log.warn("Streaming interrupted, backoff retry {}ms (attempt {}/{})",
                delay, attempt + 1, MAX_STREAM_ATTEMPTS);
        agentRetryScheduler.schedule(
                () -> startStreamAttempt(state, emitter, userId, config, attempt + 1, partial, answerBlocks),
                delay, TimeUnit.MILLISECONDS);
    }

    /** 全部尝试失败：入库失败记录（用户可在历史重试）+ 错误事件；选项叙述场景不持久化、改发选择按钮。 */
    private void finalFailure(ReActAgentState state, SseEmitter emitter, Long userId,
                              RunnableConfig config, StringBuilder partial,
                              List<Map<String, Object>> answerBlocks) {
        String answerToStore = partial.length() == 0
                ? "（回答被中断，请重新提问）"
                : partial + "\n\n（回答被中断，可重新提问重试）";
        boolean pendingChoose = state.pendingOptions() != null && !state.pendingOptions().isEmpty();
        if (!pendingChoose) {
            try {
                persistRound(userId, state.userQuery(), answerToStore, state, answerBlocks);
                appendRoundToCheckpoint(config, state.userQuery(), answerToStore);
            } catch (Exception e) {
                log.warn("persist on final failure failed: {}", e.getMessage());
            }
        }
        try {
            if (pendingChoose) {
                sendOptionsEvent(emitter, state);
            } else {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("type", "answer");
                event.put("content", answerToStore);
                emitter.send(SseEmitter.event().name("step").data(event));
            }
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (Exception e) {
            log.debug("final failure send failed: {}", e.getMessage());
        }
    }

    /** 构建可重试的流式 handler：文本流打字 + 占位符剥离发卡片（穿插）+ showCards 声明卡片实时出卡；
     *  onComplete 成功持久化；onError 指数退避重试续传。
     *  partial/answerBlocks 由外部承载（跨尝试累积，断点续传）。 */
    private StreamingChatResponseHandler buildRetryableHandler(ReActAgentState state, SseEmitter emitter,
            Long userId, RunnableConfig config, int attempt,
            StringBuilder partial, List<Map<String, Object>> answerBlocks) {
        // showCards 声明的店铺卡片（name → card）：LLM 决策阶段通过工具调用确定性声明，
        // 流式回答中按店名出现实时发独立 cards SSE 事件（anchor 定位插入，卡片位置准确）
        final Map<String, Map<String, Object>> declaredCards = loadDeclaredCards(state);
        return new StreamingChatResponseHandler() {
            private final StringBuilder streamBuf = new StringBuilder(); // [[id]] 占位符跨 chunk 缓冲
            // 续传去重（attempt>1）：LLM 可能重复 partial 末尾，发送端精确截断，前端零内容去重
            private final StreamingResumeDedupe resumeDedupe = attempt > 1
                    ? new StreamingResumeDedupe(partial.toString()) : null;
            // showCards 已发送的卡片 id（防重复）
            private final java.util.Set<String> sentDeclaredCards = new java.util.HashSet<>();

            /**
             * showCards 声明卡片实时出卡：累积已发送文本（partial）中出现声明卡片的店名 → 立即发该卡的独立
             * cards SSE 事件（带 anchor，前端在文本内定位插入）。与 [[id]] 剥离的 card 块按 id 去重。
             */
            private void emitDeclaredCards(String text) {
                if (declaredCards == null || declaredCards.isEmpty()
                        || text == null || text.isEmpty()) return;
                for (Map.Entry<String, Map<String, Object>> e : declaredCards.entrySet()) {
                    String name = e.getKey();
                    Map<String, Object> card = e.getValue();
                    String id = String.valueOf(card.get("id"));
                    if (sentDeclaredCards.contains(id)) continue;
                    // 与 [[id]] 剥离进 answerBlocks 的 card 块去重（LLM 旧行为兼容）
                    boolean already = answerBlocks.stream().anyMatch(b ->
                            "card".equals(b.get("type")) && id.equals(String.valueOf(b.get("id"))));
                    if (already) {
                        sentDeclaredCards.add(id);
                        continue;
                    }
                    // 防 md 劈开（真实事故：卡片事件先于店名后的 ** 闭合标记到达，before 块含未闭合 ** 导致 md 加粗失效）：
                    // 统一交给 resolveAnchorForSend 判定——店名在末尾/md 标记未到齐/仅主名匹配时返回 null（等或交流式结束兜底）
                    String anchor = resolveAnchorForSend(text, name);
                    if (anchor == null) continue;
                    try {
                        Map<String, Object> c = new LinkedHashMap<>(card);
                        c.put("anchor", anchor);
                        emitter.send(SseEmitter.event().name("step")
                                .data(Map.of("type", "cards", "cards", List.of(c))));
                        sentDeclaredCards.add(id);
                        Map<String, Object> cb = new LinkedHashMap<>();
                        cb.put("type", "card");
                        cb.put("id", id);
                        answerBlocks.add(cb);
                        log.info("showCards 实时出卡: {} (anchor={})", name, anchor);
                    } catch (Exception ex) {
                        log.debug("showCards cards send failed: {}", ex.getMessage());
                    }
                }
            }

            /** 发送流式文本：attempt>1 时先经续传截断（与续传前 partial 末尾精确前缀重叠去重），再发送 + 记录。 */
            private void emitStreamText(String text) {
                if (text == null || text.isEmpty()) return;
                String toSend = resumeDedupe != null ? resumeDedupe.feed(text) : text;
                if (toSend.isEmpty()) return;
                sendTextChunk(toSend, emitter, partial);
                appendTextBlock(toSend);
            }

            @Override
            public void onPartialResponse(String token) {
                // 文本流：缓冲识别 [[数字id]] 占位符 → 剥离文本（answer_chunk），占位符转独立 cards 事件（穿插）
                if (token == null || token.isEmpty()) return;
                streamBuf.append(token);
                String buf = streamBuf.toString();
                int processed = 0;
                int searchFrom = 0;
                while (true) {
                    int start = buf.indexOf("[[", searchFrom);
                    if (start < 0) break;
                    int end = buf.indexOf("]]", start);
                    if (end < 0) break; // 未闭合，等后续 chunk
                    String id = parseId(buf.substring(start + 2, end));
                    if (id == null) {
                        // 非 [[id]]（如文本里的普通 [[xx]]）：当普通文本跳过该 [[
                        searchFrom = start + 2;
                        continue;
                    }
                    String textPart = buf.substring(processed, start);
                    emitStreamText(textPart);
                    log.info("LLM 流式输出店铺占位符 [[{}]]，剥离并发卡片事件", id);
                    emitCardsEvent(id, emitter, state);
                    Map<String, Object> cb = new LinkedHashMap<>();
                    cb.put("type", "card");
                    cb.put("id", id);
                    answerBlocks.add(cb);
                    processed = end + 2;
                    searchFrom = processed;
                }
                // 剩余：未闭合 [[ 或单个 [ 结尾（[[ 被 token 拆开）→ 保留前缀等补全，之前文本照发
                String rest = buf.substring(processed);
                int openIdx = rest.indexOf("[[");
                if (openIdx >= 0 && rest.indexOf("]]", openIdx) < 0) {
                    String textPart = rest.substring(0, openIdx);
                    emitStreamText(textPart);
                    streamBuf.setLength(0);
                    streamBuf.append(rest.substring(openIdx));
                } else if (rest.endsWith("[")) {
                    // 单个 [ 结尾：可能是 [[ 被 token 拆成 [ + [，保留等后续补全
                    String textPart = rest.substring(0, rest.length() - 1);
                    emitStreamText(textPart);
                    streamBuf.setLength(0);
                    streamBuf.append("[");
                } else {
                    emitStreamText(rest);
                    streamBuf.setLength(0);
                }
                // showCards 声明卡片：按已发送文本中的店名实时发独立 cards 事件（anchor 定位）
                emitDeclaredCards(partial.toString());
            }

            /** 追加文本块；与相邻 text 块合并，避免持久化 blocks 碎片化。 */
            private void appendTextBlock(String t) {
                if (t == null || t.isEmpty()) return;
                if (!answerBlocks.isEmpty()
                        && "text".equals(answerBlocks.get(answerBlocks.size() - 1).get("type"))) {
                    Map<String, Object> last = answerBlocks.get(answerBlocks.size() - 1);
                    last.put("text", String.valueOf(last.get("text")) + t);
                } else {
                    // 必须用可变 Map：后续同类型 text 块要 put 合并（Map.of 是不可变的，put 会抛 UnsupportedOperationException）
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "text");
                    m.put("text", t);
                    answerBlocks.add(m);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                // 流式收尾：处理残留的未闭合占位符（丢弃半截 [[id]]，避免前端显示割裂字面）
                if (streamBuf.length() > 0) {
                    String rest = streamBuf.toString();
                    String clean;
                    int openIdx = rest.indexOf("[[");
                    if (openIdx >= 0) clean = rest.substring(0, openIdx);
                    else if (rest.endsWith("[")) clean = rest.substring(0, rest.length() - 1);
                    else clean = rest;
                    if (!clean.isEmpty()) {
                        emitStreamText(clean);
                    }
                    streamBuf.setLength(0);
                }
                String answer = partial.toString();
                // 可观测：LLM 流式生成的完整回答记入日志（与「ANSWER PROMPT (完整)」对照，
                // 可确认 [[id]] 是 LLM 没输出，还是被后端剥离/卡片匹配失败）
                log.info("ANSWER 输出 (完整, {} 字符):\n{}", answer.length(), answer);
                // 选项选择叙述：流式输出的是「选择前的介绍语」，不是最终回答——
                // 不持久化、不进 checkpoint；流式结束后补发选择按钮，等用户点选后 resume 回 agent。
                boolean pendingChoose = state.pendingOptions() != null && !state.pendingOptions().isEmpty();
                if (!pendingChoose) {
                    // 卡片已在流式过程中按 [[id]] 占位符位置实时发（emitCardsEvent），answerBlocks 已含对应 card 块；
                    // 持久化优先：即使前端已断开（emitter already completed），本轮回答仍要入库
                    try {
                        persistRound(userId, state.userQuery(), answer, state, answerBlocks);
                        appendRoundToCheckpoint(config, state.userQuery(), answer);
                    } catch (Exception e) {
                        log.warn("persistRound/checkpoint failed: {}", e.getMessage());
                    }
                }
                try {
                    if (pendingChoose) {
                        sendOptionsEvent(emitter, state);
                    } else {
                        // 兜底补卡：本轮 LLM 未输出 [[id]] 且回答涉及店铺时，按店名匹配补发（answer 事件之前）
                        ensureShopCards(emitter, state, answer, answerBlocks);
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("type", "answer");
                        event.put("content", answer);
                        emitter.send(SseEmitter.event().name("step").data(event));
                    }
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                } catch (Exception e) {
                    // 连接已断开 / emitter 已 complete：静默（前端已离开，无接收方）
                    log.debug("SSE complete send failed (client disconnected or already completed): {}", e.getMessage());
                }
            }


            @Override
            public void onError(Throwable error) {
                // 连接中断（如 Connection reset）：指数退避重试续传。
                // 不 complete emitter——重试时前端连接保持，续传文本继续追加（断点续传）。
                log.error("Streaming LLM error", error);
                scheduleRetry(state, emitter, userId, config, attempt, partial, answerBlocks);
            }
        };
    }

    /** Append current round (user query + AI answer) to both checkpoint and MySQL. */
    private void appendRoundToCheckpoint(RunnableConfig config, String query, String answer) {
        if (answer == null || answer.isEmpty()) return;
        // Write to checkpoint for context accumulation + sliding window compression。
        // user 请求已在图执行时由 ContextNode 追加到标准 messages 通道，这里只追加最终回答 ai。
        try {
            reactGraph.updateState(config, Map.of("messages",
                    List.of(ReActAgentState.aiMsg(answer))));
            log.info("Appended round to checkpoint: threadId={}, answerLen={}",
                    config.threadId(), answer.length());
        } catch (Exception e) {
            log.error("Failed to append round to checkpoint for {}: {}",
                    config.threadId(), e.getMessage());
        }
    }

    /**
     * 持久化一轮问答：卡片从最终转录提取最后一次店铺结果；blocks 为回答结构（text/card 顺序），
     * 历史重建时按此将卡片插到对应位置。
     */
    private void persistRound(Long userId, String query, String answer, ReActAgentState state,
                              List<Map<String, Object>> answerBlocks) {
        try {
            if (query != null && !query.isEmpty() && answer != null && !answer.isEmpty()) {
                // 最终卡片来自流式 [[id]] 占位符剥离（onPartialResponse 记入 answerBlocks 的 card 块 id）；
                // 回答未输出占位符 → 不持久化任何卡片
                List<String> ids = new ArrayList<>();
                if (answerBlocks != null) {
                    for (Map<String, Object> b : answerBlocks) {
                        if ("card".equals(b.get("type")) && b.get("id") instanceof String s
                                && !s.isBlank()) ids.add(s);
                    }
                }
                String cardsJson = null;
                if (!ids.isEmpty()) {
                    List<Map<String, Object>> cards = cardsByIds(state, ids);
                    if (cards != null && !cards.isEmpty()) cardsJson = JSONUtil.toJsonStr(cards);
                }
                chatHistoryRepo.saveRound(userId, query, answer, cardsJson, blocksToJson(answerBlocks));
            }
        } catch (Exception e) {
            log.error("Failed to persist chat round for user {}: {}", userId, e.getMessage());
        }
    }

    /** 回答结构块序列化；空/无块返回 null。 */
    private String blocksToJson(List<Map<String, Object>> answerBlocks) {
        if (answerBlocks == null || answerBlocks.isEmpty()) return null;
        return JSONUtil.toJsonStr(answerBlocks);
    }

    /** 发一段文本 chunk（answer_chunk）并累计到 partial（供持久化）。 */
    private void sendTextChunk(String text, SseEmitter emitter, StringBuilder partial) {
        if (text == null || text.isEmpty()) return;
        partial.append(text);
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "answer_chunk");
            event.put("content", text);
            // SSE 事件 id = 累计已发送字符数（单调递增）：符合标准 Last-Event-ID 语义，
            // 客户端记录 lastEventId 可定位断点，续传不重复由发送端保证。
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(partial.length()))
                    .name("step").data(event));
        } catch (Exception e) {
            // 连接断开 / emitter 已 complete（IllegalStateException）都会抛这里——静默，不级联 onError
            log.debug("SSE chunk send failed (client disconnected or already completed): {}", e.getMessage());
        }
    }

    /** 按店铺对外 ID（Sqids 字符串）从 transcript 提取卡片，发独立 cards 事件（对应商家位置渲染）。 */
    private void emitCardsEvent(String shopId, SseEmitter emitter, ReActAgentState state) {
        if (shopId == null || shopId.isBlank()) return;
        try {
            List<Map<String, Object>> cards = cardsByIds(state, List.of(shopId));
            if (cards != null && !cards.isEmpty()) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("type", "cards");
                event.put("cards", cards);
                emitter.send(SseEmitter.event().name("step").data(event));
            }
        } catch (Exception e) {
            log.warn("emitCardsEvent failed for {}: {}", shopId, e.getMessage());
        }
    }

    /** 解析 [[...]] 占位符：非空即视为有效（对外混淆 ID 为 Sqids 字符串，也可能是数字兼容旧会话）。 */
    private String parseId(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 从标准 messages 通道收集所有店铺类工具结果的卡片，按对外 ID 取指定卡片。 */
    private List<Map<String, Object>> cardsByIds(ReActAgentState state, List<String> ids) {
        if (state == null || ids == null || ids.isEmpty()) return List.of();
        Map<String, Map<String, Object>> byId = collectShopCards(state);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> c = byId.get(id);
            if (c != null) result.add(c);
        }
        return result;
    }

    /** 加载 Agent 决策阶段 showCards 声明的店铺卡片（name → card），供流式回答实时出卡。 */
    private Map<String, Map<String, Object>> loadDeclaredCards(ReActAgentState state) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        try {
            if (state == null || state.scratchpad() == null) return byName;
            Object raw = state.scratchpad().get(com.hmdp.agent.graph.nodes.AgentNode.SP_CARD_IDS);
            if (raw == null) return byName;
            List<String> ids = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) ids.add(String.valueOf(o));
                }
            } else if (raw instanceof String s && !s.isBlank()) {
                ids.add(s);
            }
            if (ids.isEmpty()) return byName;
            for (Map<String, Object> card : cardsByIds(state, ids)) {
                Object name = card.get("name");
                if (name != null && !String.valueOf(name).isBlank()) {
                    byName.put(String.valueOf(name), card);
                }
            }
        } catch (Exception e) {
            log.warn("loadDeclaredCards failed: {}", e.getMessage());
        }
        return byName;
    }

    /** 收集 messages 中店铺类工具结果（searchShops/searchShop/recommendShops/geoSearch）的全部店铺卡片，对外 ID 去重。
     *  历史旧结果的数字 id 先运行时混淆（与 AnswerInjection 折叠时一致），保证卡片 key 与 LLM 复制的占位符值匹配。 */
    private Map<String, Map<String, Object>> collectShopCards(ReActAgentState state) {        Map<String, Map<String, Object>> byId = new HashMap<>();
        if (state == null) return byId;
        for (Map<String, String> m : state.messages()) {
            if (!"tool".equals(m.get("role"))) continue;
            if (Boolean.parseBoolean(m.getOrDefault("isError", "false"))) continue;
            String toolName = m.getOrDefault("toolName", "");
            String content = m.getOrDefault("content", "");
            if (content == null || content.isBlank()) continue;
            String obfuscated = com.hmdp.utils.ShopResultIdObfuscator.obfuscate(toolName, content, idObfuscator);
            List<Map<String, Object>> cards = buildShopCards(toolName, obfuscated);
            if (cards != null) {
                for (Map<String, Object> c : cards) {
                    Object id = c.get("id");
                    if (id != null && !String.valueOf(id).isBlank()) {
                        byId.put(String.valueOf(id), c);
                    }
                }
            }
        }
        return byId;
    }

    /**
     * 兜底补卡（确定性护栏，对齐 A2UI 协议校验思路，不依赖 LLM 自觉）：
     * 本轮 LLM 完全未输出 [[id]]（answerBlocks 无 card 块）且回答涉及店铺时，
     * 按店名匹配本会话工具结果的店铺补发 cards 事件；卡片携带 anchor（命中的店名），
     * 前端据此在文本块内定位并拆分插入，保证卡片渲染在对应店铺文本之后（而非堆积在末尾）。
     */
    private void ensureShopCards(SseEmitter emitter, ReActAgentState state, String answer,
                                 List<Map<String, Object>> answerBlocks) {
        if (state == null || answer == null || answer.isBlank()) return;
        // 已有 card 块（[[id]] 剥离或 showCards 实时出卡）→ 只补未覆盖的店（按 id 去重），不全量跳过：
        // 流式实时出卡可能因「店名后 md 标记未到齐 / 仅主名匹配」而漏发个别店，由这里补齐
        java.util.Set<String> sentIds = new java.util.HashSet<>();
        if (answerBlocks != null) {
            for (Map<String, Object> b : answerBlocks) {
                if ("card".equals(b.get("type"))) sentIds.add(String.valueOf(b.get("id")));
            }
        }
        Map<String, Map<String, Object>> collected = collectShopCards(state);
        List<Map<String, Object>> matched = matchCardsByAnswer(collected, answer);
        matched.removeIf(c -> sentIds.contains(String.valueOf(c.get("id"))));
        if (matched.isEmpty()) {
            if (sentIds.isEmpty()) {
                log.info("ensureShopCards: 回答未输出 [[id]] 且按店名兜底匹配 0 张（共收集 {} 张候选卡片）——可能回答未提及候选店或工具结果无卡片", collected.size());
            }
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("step").data(Map.of("type", "cards", "cards", matched)));
            log.info("Agent cards fallback: 按店名补发 {} 张未覆盖卡片（含 anchor，已发 {} 张跳过）", matched.size(), sentIds.size());
        } catch (Exception e) {
            log.debug("cards fallback send failed: {}", e.getMessage());
        }
    }

    /**
     * 店名匹配（静态可测）：回答文本中提到某店（全名或去括号主名）→ 命中其卡片；
     * 返回的卡片带 {@code anchor} 字段 = 实际命中的店名串，供前端在文本内定位插入。
     */
    static List<Map<String, Object>> matchCardsByAnswer(Map<String, Map<String, Object>> allCards, String answer) {
        if (allCards == null || allCards.isEmpty() || answer == null || answer.isBlank()) return List.of();
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> card : allCards.values()) {
            String name = String.valueOf(card.getOrDefault("name", ""));
            if (name.isBlank()) continue;
            String anchor = findAnchor(answer, name);
            if (anchor != null) {
                Map<String, Object> c = new LinkedHashMap<>(card);
                c.put("anchor", anchor);
                matched.add(c);
            }
        }
        return matched;
    }

    /** 店名在回答文本中的命中锚点：全名优先，其次去括号主名（≥2 字符）；未命中返回 null。 */
    static String findAnchor(String answer, String name) {
        if (name == null || name.isBlank() || answer == null) return null;
        if (answer.contains(name)) return name;
        String core = coreName(name);
        return core.length() >= 2 && answer.contains(core) ? core : null;
    }

    /** markdown 行内标记字符（加粗/斜体/代码）。 */
    static boolean isMdMark(char c) {
        return c == '*' || c == '_' || c == '`';
    }

    /**
     * 计算「可立即发送」的 anchor；返回 null 表示现在还不能发（等下一 chunk 或交流式结束兜底）：
     * <ul>
     *   <li>店名未出现 → null；</li>
     *   <li><b>店名在文本末尾（after ≥ length）→ null</b>——「**店名」结尾时闭合「**」可能还没流到，
     *       立即发会让前端把未闭合的「**」劈进 before 块，md 加粗失效（真实事故）；</li>
     *   <li>店名后紧跟 md 标记（星号/下划线/反引号）→ 等标记到齐（标记序列后已有内容）才返回，anchor 含闭合标记；</li>
     *   <li>仅主名匹配（非全名，无法确定括号后缀与 md 边界）→ null，交流式结束 ensureShopCards 兜底。</li>
     * </ul>
     */
    static String resolveAnchorForSend(String text, String name) {
        String anchor = findAnchor(text, name);
        if (anchor == null) return null;
        int pos = text.indexOf(anchor);
        int after = pos + anchor.length();
        if (after >= text.length()) return null; // 店名在末尾：闭合标记可能未到，等下一 chunk
        if (isMdMark(text.charAt(after))) {
            int scan = after;
            while (scan < text.length() && isMdMark(text.charAt(scan))) scan++;
            if (scan >= text.length()) return null; // 闭合标记未到齐（chunk 尾部），等下一 chunk
            return text.substring(pos, scan);       // 锚点含闭合标记：before 块将是完整加粗
        }
        if (anchor.length() < name.length()) return null; // 仅主名匹配：交流式结束兜底
        return anchor;
    }

    /** 店名核心词：去掉括号后缀（如「牛约堡牛约汉堡(古运路店)」→「牛约堡牛约汉堡」）；核心词过短则回退全名。 */
    private static String coreName(String name) {
        int i = name.indexOf('(');
        int j = name.indexOf('（');
        int k = i >= 0 && j >= 0 ? Math.min(i, j) : Math.max(i, j);
        if (k > 0) {
            String core = name.substring(0, k).trim();
            return core.length() >= 2 ? core : name;
        }
        return name;
    }

    /** 店铺类工具结果 → 结构化卡片数据（searchShops/searchShop/recommendShops → JSON 数组；geoSearch → {"shops":[...]}）。 */
    private List<Map<String, Object>> buildShopCards(String toolName, String result) {
        if (result == null || result.isBlank()) return null;
        try {
            if ("geoSearch".equals(toolName)) {
                JSONObject obj = JSONUtil.parseObj(result);
                if (obj.get("shops") instanceof JSONArray arr) return toShopCards(arr);
            } else if ("searchShops".equals(toolName) || "searchShop".equals(toolName)
                    || "recommendShops".equals(toolName)) {
                Object parsed = JSONUtil.parse(result);
                if (parsed instanceof JSONArray arr) return toShopCards(arr);
            }
        } catch (Exception ignored) {
            // 解析失败就不发卡片
        }
        return null;
    }

    private List<Map<String, Object>> toShopCards(JSONArray arr) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Object o : arr) {
            if (!(o instanceof JSONObject jo)) continue;
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", jo.get("id"));
            card.put("name", jo.getStr("name", ""));
            card.put("image", jo.getStr("image", ""));
            card.put("score", jo.get("score"));
            card.put("avgPrice", jo.get("avgPrice"));
            card.put("comments", jo.get("comments"));
            card.put("address", jo.getStr("address", ""));
            card.put("area", jo.getStr("area", ""));
            card.put("foodCategory", jo.getStr("foodCategory", ""));
            card.put("openHours", jo.getStr("openHours", ""));
            // 推荐模式字段（recommendShops）：推荐理由 + 基础信息
            card.put("reason", jo.getStr("reason", ""));
            card.put("distance", jo.get("distance"));
            card.put("petFriendly", jo.getBool("petFriendly", false));
            card.put("childFriendly", jo.getBool("childFriendly", false));
            card.put("hasParking", jo.getBool("hasParking", false));
            card.put("signatureDishes", jo.getStr("signatureDishes", ""));
            card.put("reviewSummary", jo.getStr("reviewSummary", ""));
            card.put("reviewRating", jo.get("reviewRating"));
            card.put("reviewCount", jo.get("reviewCount"));
            cards.add(card);
        }
        return cards;
    }

    /**
     * 写操作确认卡片（协议适配层）：pendingWrite JSON → 前端确认卡片。
     *
     * <p>卡片 schema（kind/字段语义、何时出卡片）由对应 skill 的 references 定义
     * （取号/取消排队见 {@code agent-skills/queue/references/queue-confirmation.md}），
     * 此处只做「写操作参数 → 卡片 JSON」的通用序列化适配，不承载业务规范。
     * 未知写工具返回 null（仅展示确认/取消按钮，不附卡片）。
     */
    private Map<String, Object> buildWriteConfirmCard(String pendingWrite, Long userId) {
        try {
            JSONObject obj = JSONUtil.parseObj(pendingWrite);
            String tool = obj.getStr("tool");
            Object argsObj = obj.get("args");
            if (!(argsObj instanceof JSONObject args)) return null;

            if ("takeQueueNumber".equals(tool)) {
                // 取号卡片 kind=queue：店名 + 人数（缺省 2）——schema 见 queue skill references
                // shopId 为 LLM 回传的对外混淆 ID，先还原再查店名
                Long shopId = idObfuscator.decodeOrId(args.getStr("shopId"));
                Integer people = args.getInt("peopleCount");
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("kind", "queue");
                card.put("shopName", shopName(shopId));
                card.put("peopleCount", people != null && people > 0 ? people : 2);
                return card;
            }
            if ("cancelMyQueue".equals(tool)) {
                // 取消排队卡片 kind=queue-cancel：展示当前用户的排队状况（店名/排队号/人数/状态），
                // 不依赖 LLM 传的 ticketId——直接按登录用户查最新排队中记录，前端据此渲染确认内容。
                // schema 见 queue skill references/queue-confirmation.md（"引用 queryMyQueueStatus() 的真实排队记录"）
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("kind", "queue-cancel");
                if (userId != null) {
                    try {
                        List<Map<String, Object>> rows = jdbc.queryForList(
                                "SELECT s.name AS shop_name, t.queue_number, t.people_count, t.status " +
                                "FROM tb_queue_ticket t JOIN tb_shop s ON t.shop_id = s.id " +
                                "WHERE t.user_id = ? AND t.status IN (0,1) " +
                                "ORDER BY t.create_time DESC LIMIT 1", userId);
                        if (!rows.isEmpty()) {
                            Map<String, Object> r = rows.get(0);
                            card.put("shopName", String.valueOf(r.get("shop_name")));
                            Object qn = r.get("queue_number");
                            if (qn != null) card.put("queueNumber", qn);
                            Object pc = r.get("people_count");
                            if (pc != null) card.put("peopleCount", pc);
                            Object st = r.get("status");
                            if (st != null) card.put("status", queueStatusText(((Number) st).intValue()));
                        } else {
                            card.put("noQueue", true); // 当前无排队中记录，前端提示
                        }
                    } catch (Exception ignored) {
                        // 排队记录查不到就不带字段，前端按缺省渲染
                    }
                }
                return card;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 排队状态文案（与 QueueTicketServiceImpl.statusDesc 一致）。 */
    private static String queueStatusText(int status) {
        switch (status) {
            case 0: return "排队中";
            case 1: return "已叫号";
            case 2: return "已取消";
            case 3: return "已完成";
            default: return "未知";
        }
    }

    /** 按店铺内部 ID 查店名（确认卡片用）；查不到返回空串。 */
    private String shopName(Long shopId) {
        if (shopId == null) return "";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT name FROM tb_shop WHERE id = ?", shopId);
            return rows.isEmpty() ? "" : String.valueOf(rows.get(0).get("name"));
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 解析 pendingOptions 的 JSON 数组 → 前端 actions 按钮列表（兼容字符串选项与 {label,value} 对象）。 */
    private List<Map<String, Object>> parseOptions(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JSONArray arr = JSONUtil.parseArray(json);
            for (Object o : arr) {
                Map<String, Object> item = new LinkedHashMap<>();
                if (o instanceof JSONObject jo) {
                    item.put("label", jo.getStr("label", ""));
                    item.put("value", jo.getStr("value", jo.getStr("label", "")));
                } else if (o instanceof String s) {
                    item.put("label", s);
                    item.put("value", s);
                } else {
                    continue;
                }
                out.add(item);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String planSummary(String planJson) {
        if (planJson == null || planJson.isEmpty()) return "正在分析意图...";
        try {
            int i = planJson.indexOf("\"intent\"");
            if (i >= 0) {
                int start = planJson.indexOf("\"", i + 8);
                if (start >= 0) {
                    int end = planJson.indexOf("\"", start + 1);
                    if (end > start) {
                        String intent = planJson.substring(start + 1, end);
                        if (intent.length() > 60) intent = intent.substring(0, 60) + "...";
                        return "理解意图: " + intent;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "正在分析意图...";
    }

    private String buildFallbackAnswer(ReActAgentState state) {
        Map<String, Object> sp = state.scratchpad();
        boolean hasData = sp != null && sp.entrySet().stream()
                .anyMatch(e -> !e.getKey().startsWith("_") && e.getValue() != null
                        && !e.getValue().toString().isEmpty());

        if (hasData) {
            return "抱歉，查询过程有些复杂，未能完成全部分析。以下是已获取的部分信息，您可以参考或换个方式再问。";
        }
        return "抱歉，当前无法完成您的请求。请尝试换个更具体的问法，我会尽力帮您。";
    }

    private void completeWithError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of(
                    "type", "error",
                    "message", friendlyError(msg))));
            emitter.complete();
        } catch (Exception ignored) {
            // emitter 已 complete / 连接已断：忽略，避免 onError 内部再抛
        }
    }

    /** 底层异常（连接池/SQL 等）转成用户友好文案，不暴露技术细节。 */
    private static String friendlyError(String raw) {
        if (raw == null || raw.isBlank()) return "服务繁忙，请稍后重试。";
        String lower = raw.toLowerCase();
        if (lower.contains("connection") || lower.contains("timeout") || lower.contains("sql")
                || lower.contains("postgres") || lower.contains("hikari") || lower.contains("checkpoint")) {
            return "服务繁忙，请稍后重试。";
        }
        return raw.length() > 120 ? raw.substring(0, 120) + "…" : raw;
    }

    private Map<String, Object> buildInit(String threadId, String message, Long userId,
                                          String centerX, String centerY, String districtIdStr) {
        return GraphInputFactory.newInit(threadId, message, userId, centerX, centerY, districtIdStr);
    }
}
