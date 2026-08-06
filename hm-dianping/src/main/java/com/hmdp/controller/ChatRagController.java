package com.hmdp.controller;

import com.hmdp.agent.CustomerServiceAgent;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.guard.ReflectionGuard;
import com.hmdp.dto.ChatHistoryRound;
import com.hmdp.dto.ChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.rag.retrieval.RetrievalService;
import com.hmdp.repository.ChatHistoryRepository;
import com.hmdp.utils.RedisConstants;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/chat")
public class ChatRagController {

    private static final Logger log = LoggerFactory.getLogger(ChatRagController.class);

    @Resource
    private CustomerServiceAgent agent;

    @Resource
    private RetrievalService retrievalService;

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @Resource
    private OpenAiChatModel chatModel;

    @Resource
    private ChatHistoryRepository chatHistoryRepo;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** RAG + Agent 模式 */
    @PostMapping("/rag")
    public Result ragChat(@RequestBody ChatRequestDTO request) {
        if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
            return Result.fail("会话ID不能为空");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        try {
            String context = retrievalService.searchAsContext(request.getMessage());
            String msg = context.isEmpty() ? request.getMessage()
                    : context + "\n\n# 用户问题\n" + request.getMessage();
            String reply = agent.chat(request.getSessionId(), msg);
            return Result.ok(ReflectionGuard.apply(reply));
        } catch (Exception e) {
            log.error("RAG chat error", e);
            return Result.fail("AI客服暂时不可用");
        }
    }

    /** ReAct Graph 模式 — 强制登录，从 Redis 获取 userId */
    @PostMapping("/react")
    public Result reactChat(@RequestBody ChatRequestDTO request,
                            HttpServletRequest httpRequest) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }

        Long userId = resolveUserIdFromRedis(httpRequest);
        if (userId == null || userId <= 0) {
            return Result.fail("请先登录");
        }

        try {
            String threadId = "user:" + userId;

            Map<String, Object> init = new LinkedHashMap<>();
            init.put("sessionId", threadId);
            init.put("userQuery", request.getMessage());
            init.put("iteration", 0);
            init.put("toolFailures", 0);
            init.put("scratchpad", new LinkedHashMap<>());
            init.put("messages", new ArrayList<>());
            init.put("compressedSummary", "");
            init.put("nextNode", "context");
            init.put("streamingPrompt", "");
            init.put("observerReport", "");
            init.put("userId", userId);

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            Optional<ReActAgentState> result = reactGraph.invoke(init, config);
            String answer;
            boolean answerFromStreaming = false;
            if (result.isPresent()) {
                ReActAgentState state = result.get();
                if ("__STREAMING__".equals(state.finalAnswer())) {
                    answer = generateSyncAnswer(state);
                    answerFromStreaming = true;
                } else {
                    answer = state.finalAnswer() != null
                            ? state.finalAnswer()
                            : "系统处理完成，但未生成回答。";
                }
            } else {
                answer = "系统处理完成，但未生成回答。";
            }

            // Persist Q&A: MySQL (for history API) + checkpoint (for context accumulation)
            if (request.getMessage() != null && !request.getMessage().isEmpty()
                    && answer != null && !answer.isEmpty()) {
                try {
                    chatHistoryRepo.saveRound(userId, request.getMessage(), answer);
                } catch (Exception e) {
                    log.error("Failed to persist chat round for user {}: {}", userId, e.getMessage());
                }
                if (answerFromStreaming) {
                    // AnswerNode didn't add messages to state — do it here
                    try {
                        reactGraph.updateState(config, Map.of("messages",
                                List.of(ReActAgentState.userMsg(request.getMessage()),
                                        ReActAgentState.aiMsg(answer))));
                    } catch (Exception e) {
                        log.error("Failed to update checkpoint for user {}: {}", userId, e.getMessage());
                    }
                }
            }

            log.info("ReAct complete: threadId={}, messages={}",
                    threadId,
                    result.map(s -> s.messages().size()).orElse(0));

            return Result.ok(answer);
        } catch (Exception e) {
            log.error("ReAct error for user {}", userId, e);
            return Result.fail("AI客服处理失败: " + e.getMessage());
        }
    }

    /**
     * 聊天历史 — 分页查询，从 PostgreSQL tb_chat_history 加载。
     */
    @GetMapping("/history")
    public Result history(
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            HttpServletRequest httpRequest) {

        Long userId = resolveUserIdFromRedis(httpRequest);
        if (userId == null || userId <= 0) {
            return Result.fail("请先登录");
        }

        if (limit < 1 || limit > 20) {
            limit = 10;
        }

        List<ChatHistoryRound> rounds = chatHistoryRepo.findRounds(userId, beforeId, limit);

        boolean hasMore = false;
        if (!rounds.isEmpty()) {
            ChatHistoryRound oldest = rounds.get(rounds.size() - 1);
            Long minId = chatHistoryRepo.getMinId(userId);
            hasMore = minId != null && oldest.getId() > minId;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rounds", rounds);
        result.put("hasMore", hasMore);

        return Result.ok(result);
    }

    /** 非流式端点用同步模型生成最终回答（AnswerNode 设置了 __STREAMING__ 标记时） */
    private String generateSyncAnswer(ReActAgentState state) {
        String prompt = state.streamingPrompt();
        if (prompt == null || prompt.isEmpty()) {
            return "系统处理完成，但未生成回答。";
        }
        try {
            ChatResponse resp = chatModel.chat(List.of(
                    SystemMessage.from("你是生活优选AI客服小优。友好、专业、简洁。"),
                    UserMessage.from(prompt)));
            return resp.aiMessage().text();
        } catch (Exception e) {
            log.error("Sync answer generation failed", e);
            return "抱歉，回答生成失败，请稍后重试。";
        }
    }

    /** 从 Redis 直接获取当前用户 ID（分布式友好，不依赖 ThreadLocal） */
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
}
