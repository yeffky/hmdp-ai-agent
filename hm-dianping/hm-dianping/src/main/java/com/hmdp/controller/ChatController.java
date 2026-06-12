package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.agent.CustomerServiceAgent;
import com.hmdp.agent.guard.ReflectionGuard;
import com.hmdp.dto.ChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.ChatMessage;
import com.hmdp.mapper.ChatMessageMapper;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Resource
    private CustomerServiceAgent agent;

    @Resource
    private ChatMessageMapper chatMessageMapper;

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

    @GetMapping("/history")
    public Result history(@RequestParam("sessionId") String sessionId,
                          @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.fail("会话ID不能为空");
        }
        List<ChatMessage> list = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("create_time")
                        .last("LIMIT " + limit));
        return Result.ok(list);
    }
}
