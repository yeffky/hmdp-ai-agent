package com.hmdp.controller;

import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.utils.UserHolder;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.async.AsyncGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ReAct Graph 流式输出控制器 — SSE 推送每个节点的执行状态。
 */
@RestController
@RequestMapping("/chat")
public class ReactStreamController {

    private static final Logger log = LoggerFactory.getLogger(ReactStreamController.class);

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @PostMapping(value = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamReact(@RequestBody Map<String, String> request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            completeWithError(emitter, "消息不能为空");
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Long userId = getCurrentUserId();
                String threadId;
                if (userId != null && userId > 0) {
                    threadId = "user:" + userId;
                } else {
                    String sessionId = request.getOrDefault("sessionId", UUID.randomUUID().toString().substring(0, 8));
                    threadId = "anon:" + sessionId;
                }

                RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

                Map<String, Object> init = buildInit(threadId, message, userId);
                AsyncGenerator<NodeOutput<ReActAgentState>> stream =
                        reactGraph.stream(init, config);

                stream.iterator().forEachRemaining(output -> {
                    try {
                        ReActAgentState state = output.state();
                        String node = output.node();
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("node", node);

                        switch (node) {
                            case "context":
                                event.put("type", "thinking");
                                event.put("content", "正在整理上下文...");
                                break;
                            case "planner":
                                event.put("type", "thinking");
                                event.put("content", "正在分析意图...");
                                event.put("plan", state.planJson());
                                break;
                            case "executor":
                                event.put("type", "tool");
                                Map<String, Object> sp = state.scratchpad();
                                event.put("scratchpad", sp);
                                String lastTool = (String) sp.get("_last_tool");
                                String lastResult = (String) sp.get("_last_result");
                                if (lastTool != null) {
                                    event.put("toolName", lastTool);
                                }
                                if (lastResult != null) {
                                    event.put("toolResult", lastResult.length() > 500
                                            ? lastResult.substring(0, 500) + "..." : lastResult);
                                    event.put("content", "工具 " + lastTool + " 返回结果");
                                } else {
                                    event.put("content", "正在调用工具获取信息...");
                                }
                                break;
                            case "retryGate":
                                event.put("type", "retry");
                                event.put("content", "工具执行遇到临时问题，正在进行第 " +
                                    state.retryCount() + " 次重试...");
                                event.put("retryCount", state.retryCount());
                                event.put("tool", state.lastToolName());
                                break;
                            case "observer":
                                event.put("type", "thinking");
                                event.put("content", "正在评估信息是否充足...");
                                event.put("nextNode", state.nextNode());
                                break;
                            case "answer":
                                String answer = state.finalAnswer();
                                if (answer == null || answer.isEmpty()) {
                                    // fallback: 从 scratchpad 拼接
                                    answer = "抱歉，暂时无法处理您的问题，请稍后重试。";
                                    log.warn("Answer node produced empty finalAnswer, using fallback");
                                }
                                event.put("type", "answer");
                                event.put("content", answer);
                                log.info("SSE answer: {}", answer.length() > 80
                                        ? answer.substring(0, 80) + "..." : answer);
                                break;
                        }

                        emitter.send(SseEmitter.event()
                                .name("step")
                                .data(event));
                    } catch (IOException e) {
                        log.debug("SSE send failed (client disconnected): {}", e.getMessage());
                    }
                });

                emitter.send(SseEmitter.event().name("done").data("{}"));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming ReAct error", e);
                completeWithError(emitter, e.getMessage());
            }
        });

        return emitter;
    }

    private void completeWithError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", msg)));
            emitter.complete();
        } catch (IOException ignored) {}
    }

    private Long getCurrentUserId() {
        try {
            if (UserHolder.getUser() != null) return UserHolder.getUser().getId();
        } catch (Exception ignored) {}
        return null;
    }

    /** 构建新对话的初始状态 */
    private Map<String, Object> buildInit(String threadId, String message, Long userId) {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put("sessionId", threadId);
        init.put("userQuery", message);
        init.put("iteration", 0);
        init.put("toolFailures", 0);
        init.put("retryCount", 0);
        init.put("errorCategory", "");
        init.put("lastToolName", "");
        init.put("lastToolArgs", "");
        init.put("fatalErrorCount", 0);
        init.put("scratchpad", new LinkedHashMap<>());
        init.put("messages", new ArrayList<>());
        init.put("compressedSummary", "");
        init.put("nextNode", "context");
        init.put("finalAnswer", "");
        init.put("planJson", "");
        init.put("observerFeedback", "");
        init.put("contextBlock", "");
        if (userId != null) init.put("userId", userId);
        return init;
    }
}
