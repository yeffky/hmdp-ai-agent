package com.hmdp.controller;

import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.repository.ChatHistoryRepository;
import com.hmdp.utils.RedisConstants;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.data.message.SystemMessage;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/chat")
public class ReactStreamController {

    private static final Logger log = LoggerFactory.getLogger(ReactStreamController.class);

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @Resource
    private OpenAiStreamingChatModel streamingModel;

    @Resource
    private OpenAiChatModel chatModel;

    @Resource
    private ChatHistoryRepository chatHistoryRepo;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping(value = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamReact(@RequestBody Map<String, String> request,
                                  HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(120_000L);

        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            completeWithError(emitter, "消息不能为空");
            return emitter;
        }

        Long userId = resolveUserIdFromRedis(httpRequest);
        log.info("ReactStreamController: resolved userId={} from Redis", userId);

        // 立即发送 SSE comment 事件，冲开代理/容器的 response buffer，
        // 确保后续 token 实时到达前端，避免被缓冲为一整块
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().comment("connected"));
                log.debug("SSE heartbeat sent, connection established");
            } catch (IOException e) {
                log.debug("SSE heartbeat failed: {}", e.getMessage());
            }
        }).thenRunAsync(() -> {
            try {
                if (userId == null || userId <= 0) {
                    completeWithError(emitter, "请先登录");
                    return;
                }

                String threadId = "user:" + userId;
                RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
                Map<String, Object> init = buildInit(threadId, message, userId);
                AsyncGenerator<NodeOutput<ReActAgentState>> stream =
                        reactGraph.stream(init, config);

                ReActAgentState[] lastState = {null};

                stream.iterator().forEachRemaining(output -> {
                    try {
                        lastState[0] = output.state();
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
                                String planJson = lastState[0].planJson();
                                event.put("content", planSummary(planJson));
                                event.put("plan", planJson);
                                break;
                            case "executor":
                                Map<String, Object> sp = lastState[0].scratchpad();
                                String lastTool = (String) sp.get("_last_tool");
                                String lastResult = (String) sp.get("_last_result");
                                if (lastTool != null) {
                                    event.put("type", "tool");
                                    event.put("toolName", lastTool);
                                    event.put("scratchpad", sp);
                                    if (lastResult != null) {
                                        event.put("toolResult", lastResult.length() > 500
                                                ? lastResult.substring(0, 500) + "..." : lastResult);
                                        event.put("content", "调用 " + lastTool + " 获取信息");
                                    } else {
                                        event.put("content", "正在调用 " + lastTool + "...");
                                    }
                                } else {
                                    event.put("type", "thinking");
                                    event.put("content", "正在调用工具获取信息...");
                                }
                                break;
                            case "retryGate":
                                event.put("type", "retry");
                                event.put("content", "工具执行遇到临时问题，正在进行第 " +
                                    lastState[0].retryCount() + " 次重试...");
                                event.put("retryCount", lastState[0].retryCount());
                                event.put("tool", lastState[0].lastToolName());
                                break;
                            case "observer":
                                event.put("type", "thinking");
                                String obsNext = lastState[0].nextNode();
                                event.put("nextNode", obsNext);
                                if ("answer".equals(obsNext)) {
                                    event.put("content", "信息收集完毕，正在生成回答...");
                                } else {
                                    event.put("content", "工具结果分析完成，正在判断信息是否充足...");
                                }
                                break;
                            case "answer":
                                String answer = lastState[0].finalAnswer();
                                if ("__STREAMING__".equals(answer)) {
                                    event.put("type", "thinking");
                                    event.put("content", "正在生成回答...");
                                } else if (answer != null && !answer.isEmpty()) {
                                    event.put("type", "answer");
                                    event.put("content", answer);
                                } else {
                                    event.put("type", "thinking");
                                    event.put("content", "正在生成回答...");
                                }
                                break;
                        }

                        emitter.send(SseEmitter.event()
                                .name("step")
                                .data(event));
                    } catch (IOException e) {
                        log.debug("SSE send failed (client disconnected): {}", e.getMessage());
                    }
                });

                // After graph completes, handle streaming or preset answer
                if (lastState[0] != null) {
                    String finalAnswer = lastState[0].finalAnswer();

                    if ("__STREAMING__".equals(finalAnswer)) {
                        doStreamingAnswer(lastState[0], emitter, userId);
                    } else {
                        finishPresetAnswer(lastState[0], emitter, userId);
                    }
                } else {
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("Streaming ReAct error", e);
                completeWithError(emitter, e.getMessage());
            }
        });

        return emitter;
    }

    /** True token-by-token streaming via OpenAiStreamingChatModel + StreamingChatResponseHandler */
    private void doStreamingAnswer(ReActAgentState state, SseEmitter emitter, Long userId) {
        String prompt = state.streamingPrompt();
        if (prompt == null || prompt.isEmpty()) {
            completeWithError(emitter, "生成回答失败");
            return;
        }

        try {
            StringBuilder fullAnswer = new StringBuilder();
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from("你是黑马点评AI客服小黑。友好、专业、简洁。"),
                            UserMessage.from(prompt)
                    ))
                    .build();

            streamingModel.chat(chatRequest, ChatRequestOptions.EMPTY,
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String token) {
                            fullAnswer.append(token);
                            log.debug("SSE token: '{}'", token);
                            try {
                                Map<String, Object> event = new LinkedHashMap<>();
                                event.put("type", "answer_chunk");
                                event.put("content", token);
                                emitter.send(SseEmitter.event()
                                        .name("step")
                                        .data(event));
                            } catch (IOException e) {
                                log.debug("SSE chunk send failed (client disconnected)");
                            }
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            try {
                                String answer = fullAnswer.toString();
                                if (answer.isEmpty() && response.aiMessage() != null) {
                                    answer = response.aiMessage().text();
                                }
                                Map<String, Object> event = new LinkedHashMap<>();
                                event.put("type", "answer");
                                event.put("content", answer);
                                emitter.send(SseEmitter.event().name("step").data(event));

                                persistRound(userId, state.userQuery(), answer);
                                emitter.send(SseEmitter.event().name("done").data("{}"));
                                emitter.complete();
                            } catch (IOException e) {
                                log.debug("SSE complete send failed");
                                emitter.completeWithError(e);
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("Streaming LLM error, falling back to sync model", error);
                            try {
                                ChatResponse resp = chatModel.chat(List.of(
                                        SystemMessage.from("你是黑马点评AI客服小黑。友好、专业、简洁。"),
                                        UserMessage.from(prompt)));
                                String answer = resp.aiMessage().text();
                                Map<String, Object> event = new LinkedHashMap<>();
                                event.put("type", "answer");
                                event.put("content", answer);
                                emitter.send(SseEmitter.event().name("step").data(event));
                                persistRound(userId, state.userQuery(), answer);
                                emitter.send(SseEmitter.event().name("done").data("{}"));
                                emitter.complete();
                            } catch (Exception e) {
                                completeWithError(emitter, "回答生成失败");
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to start streaming", e);
            completeWithError(emitter, "回答生成失败");
        }
    }

    private void finishPresetAnswer(ReActAgentState state, SseEmitter emitter, Long userId) {
        try {
            String answer = state.finalAnswer();
            if (answer != null && !answer.isEmpty()) {
                persistRound(userId, state.userQuery(), answer);
            }
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (IOException e) {
            log.debug("SSE done send failed");
        }
    }

    private void persistRound(Long userId, String query, String answer) {
        try {
            if (query != null && !query.isEmpty() && answer != null && !answer.isEmpty()) {
                chatHistoryRepo.saveRound(userId, query, answer);
            }
        } catch (Exception e) {
            log.error("Failed to persist chat round for user {}: {}", userId, e.getMessage());
        }
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

    private Long resolveUserIdFromRedis(HttpServletRequest request) {
        try {
            String token = request.getHeader("authorization");
            if (token == null || token.isBlank()) {
                return null;
            }
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                    .entries(RedisConstants.LOGIN_USER_KEY + token);
            if (userMap.isEmpty()) {
                return null;
            }
            Object idObj = userMap.get("id");
            if (idObj != null) {
                return Long.valueOf(idObj.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve userId from Redis: {}", e.getMessage());
        }
        return null;
    }

    private void completeWithError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", msg)));
            emitter.complete();
        } catch (IOException ignored) {}
    }

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
        init.put("streamingPrompt", "");
        init.put("observerReport", "");
        if (userId != null) init.put("userId", userId);
        return init;
    }
}
