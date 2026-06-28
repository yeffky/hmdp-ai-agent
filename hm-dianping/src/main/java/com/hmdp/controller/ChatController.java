package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.agent.PlainCustomerServiceAgent;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.guard.ReflectionGuard;
import com.hmdp.dto.ChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.ChatMessage;
import com.hmdp.mapper.ChatMessageMapper;
import com.hmdp.utils.UserHolder;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.*;

/**
 * 聊天控制器 — /chat/send（纯 LLM）+ /chat/history（双源：图状态 + MySQL）
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Resource
    private PlainCustomerServiceAgent agent;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @PostMapping("/send")
    public Result send(@RequestBody ChatRequestDTO request) {
        if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
            return Result.fail("会话ID不能为空");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        try {
            String reply = agent.chat(request.getSessionId(), request.getMessage());
            reply = ReflectionGuard.apply(reply);
            return Result.ok(reply);
        } catch (Exception e) {
            log.error("AI chat error for session {}", request.getSessionId(), e);
            return Result.fail("AI客服暂时不可用，请稍后再试");
        }
    }

    /**
     * 历史消息 — 优先从图状态 checkpoint 读取，miss 回退 MySQL。
     * threadId 逻辑与 /chat/react 一致：登录用户用 userId，匿名用户用 sessionId。
     */
    @GetMapping("/history")
    public Result history(@RequestParam("sessionId") String sessionId,
                          @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.fail("会话ID不能为空");
        }

        // 1) 尝试从图状态读取（ReAct 模式）
        List<Map<String, String>> graphMessages = loadFromGraphState(sessionId);
        if (graphMessages != null && !graphMessages.isEmpty()) {
            log.debug("History from graph state: {} messages", graphMessages.size());
            // 截断到 limit
            if (graphMessages.size() > limit) {
                graphMessages = graphMessages.subList(graphMessages.size() - limit, graphMessages.size());
            }
            return Result.ok(graphMessages);
        }

        // 2) 回退 MySQL（/chat/rag 模式）
        List<ChatMessage> dbMessages = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("create_time")
                        .last("LIMIT " + limit));
        log.debug("History from MySQL: {} messages", dbMessages.size());
        return Result.ok(dbMessages);
    }

    /** 从图状态 checkpoint 加载消息历史 */
    private List<Map<String, String>> loadFromGraphState(String sessionId) {
        try {
            String threadId = resolveThreadId(sessionId);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            org.bsc.langgraph4j.state.StateSnapshot<ReActAgentState> snapshot =
                    reactGraph.getState(config);

            if (snapshot != null && snapshot.state() != null) {
                List<Map<String, String>> messages = snapshot.state().messages();
                if (messages != null && !messages.isEmpty()) {
                    return new ArrayList<>(messages);
                }
            }
        } catch (Exception e) {
            log.debug("Graph state history unavailable for session {}: {}", sessionId, e.getMessage());
        }
        return Collections.emptyList();
    }

    /** 与 /chat/react 保持一致的 threadId 策略 */
    private String resolveThreadId(String sessionId) {
        Long userId = getCurrentUserId();
        if (userId != null && userId > 0) {
            return "user:" + userId;
        }
        return "anon:" + (sessionId != null ? sessionId : UUID.randomUUID().toString().substring(0, 8));
    }

    private Long getCurrentUserId() {
        try {
            if (UserHolder.getUser() != null) {
                return UserHolder.getUser().getId();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
