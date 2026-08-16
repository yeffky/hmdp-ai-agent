package com.hmdp.controller;

import com.hmdp.agent.graph.GraphInputFactory;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.dto.ChatHistoryRound;
import com.hmdp.dto.ChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.repository.ChatHistoryRepository;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.UserResolver;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/chat")
public class ChatRagController {

    private static final Logger log = LoggerFactory.getLogger(ChatRagController.class);

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @Resource
    private ChatModel chatModel;

    @Resource
    private ChatHistoryRepository chatHistoryRepo;

    @Resource
    private com.hmdp.agent.memory.context.AnswerInjection answerInjection;

    @Resource
    private JwtUtil jwtUtil;

    /** ReAct Graph 模式 — 强制登录，从 Redis 获取 userId */
    @PostMapping("/react")
    public Result reactChat(@RequestBody ChatRequestDTO request,
                            HttpServletRequest httpRequest) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }

        Long userId = UserResolver.resolveUserId(httpRequest, jwtUtil);
        if (userId == null || userId <= 0) {
            return Result.fail("请先登录");
        }

        try {
            String threadId = "user:" + userId;

            // 与流式入口共用同一初始化工厂，保证初始状态形状一致（含定位/地区/全部默认字段）
            Map<String, Object> init = GraphInputFactory.newInit(threadId, request.getMessage(), userId,
                    request.getCenterX(), request.getCenterY(), request.getDistrictId());

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            Optional<ReActAgentState> result = reactGraph.invoke(init, config);
            String answer;
            boolean answerFromStreaming = false;
            if (result.isPresent()) {
                ReActAgentState state = result.get();
                if (StateKeys.SENTINEL_STREAMING.equals(state.finalAnswer())) {
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
                    chatHistoryRepo.saveRound(userId, request.getMessage(), answer, null, null);
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
            // 异常详情只在服务端日志，不向用户暴露技术细节
            return Result.fail("服务繁忙，请稍后重试。");
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

        Long userId = UserResolver.resolveUserId(httpRequest, jwtUtil);
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
        try {
            ChatResponse resp = chatModel.chat(answerInjection.build(state));
            return resp.aiMessage().text();
        } catch (Exception e) {
            log.error("Sync answer generation failed", e);
            return "抱歉，回答生成失败，请稍后重试。";
        }
    }
}
