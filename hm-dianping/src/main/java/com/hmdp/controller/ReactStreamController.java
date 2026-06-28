package com.hmdp.controller;

import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.utils.UserHolder;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.StateSnapshot;
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

                // 检查是否存在 checkpoint（上次 ask_user 中断或正常对话）
                AsyncGenerator<NodeOutput<ReActAgentState>> stream;
                Optional<StateSnapshot<ReActAgentState>> snapshotOpt =
                        reactGraph.lastStateOf(config);

                if (snapshotOpt.isPresent() && snapshotOpt.get().state() != null) {
                    ReActAgentState lastState = snapshotOpt.get().state();
                    Map<String, Object> sp = lastState.scratchpad();

                    if (sp.containsKey("ask_user_missing")) {
                        // 判断用户是在回答追问还是开了新话题
                        boolean isNewQuestion = message.length() > 20
                                && (message.contains("?") || message.contains("？")
                                    || message.contains("吗") || message.contains("什么")
                                    || message.contains("怎么") || message.contains("帮我")
                                    || message.contains("查询") || message.contains("重新开始"));

                        if (isNewQuestion) {
                            log.info("Detected new question, starting fresh for {}", threadId);
                            // 新话题：清掉旧 checkpoint，走正常新对话路径
                            reactGraph.updateState(config,
                                    Map.of("scratchpad", new LinkedHashMap<>(),
                                           "finalAnswer", "",
                                           "planJson", ""),
                                    "context");
                            Map<String, Object> init = buildInit(threadId, message, userId);
                            stream = reactGraph.stream(init, config);
                        } else {
                            // ====== 恢复现场：用户提供了缺失信息 ======
                            log.info("Resuming from ask_user checkpoint for thread {}", threadId);
                            sp.remove("ask_user_missing");       // 清除中断标记

                            // 清除上次残留的 error 和 result 条目
                            sp.remove("error");
                            sp.keySet().removeIf(k -> k.toString().startsWith("result_"));

                            sp.put("user_response", message);

                            // 合并原始问题 + 用户补充
                            String originalQuery = lastState.userQuery();
                            String mergedQuery = originalQuery + "\n用户补充：" + message;

                            Map<String, Object> updates = new LinkedHashMap<>();
                            updates.put("userQuery", mergedQuery);
                            updates.put("scratchpad", sp);
                            updates.put("finalAnswer", "");
                            updates.put("planJson", "");
                            updates.put("iteration", 0);
                            updates.put("toolFailures", 0);
                            updates.put("nextNode", "planner");

                            RunnableConfig resumedConfig = reactGraph.updateState(
                                    config, updates, "planner");
                            stream = reactGraph.stream(
                                    (Map<String, Object>) null, resumedConfig);
                        }
                    } else {
                        // 正常 checkpoint 恢复（用户连续对话）
                        Map<String, Object> init = buildInit(threadId, message, userId);
                        stream = reactGraph.stream(init, config);
                    }
                } else {
                    // 首次对话
                    Map<String, Object> init = buildInit(threadId, message, userId);
                    stream = reactGraph.stream(init, config);
                }

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
                                event.put("content", "正在调用工具获取信息...");
                                event.put("scratchpad", state.scratchpad());
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
        init.put("scratchpad", new LinkedHashMap<>());
        init.put("messages", new ArrayList<>());
        init.put("compressedSummary", "");
        init.put("nextNode", "context");
        if (userId != null) init.put("userId", userId);
        return init;
    }
}
