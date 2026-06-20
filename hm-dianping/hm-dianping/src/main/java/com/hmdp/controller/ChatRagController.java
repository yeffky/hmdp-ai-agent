package com.hmdp.controller;

import com.hmdp.agent.CustomerServiceAgent;
import com.hmdp.agent.guard.ReflectionGuard;
import com.hmdp.dto.ChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.rag.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * RAG 增强聊天控制器 — 检索增强生成的最短闭环。
 *
 * /chat/rag  = 检索 → 拼上下文 → LLM 生成 → 返回
 * /chat/send = 原有 Agent 智能客服（现在支持 RAG 工具调用）
 */
@RestController
@RequestMapping("/chat")
public class ChatRagController {

    private static final Logger log = LoggerFactory.getLogger(ChatRagController.class);

    @Resource
    private CustomerServiceAgent agent;

    @Resource
    private RetrievalService retrievalService;

    /**
     * RAG 增强对话 — 先检索知识库，再将检索结果作为上下文传给 LLM。
     */
    @PostMapping("/rag")
    public Result ragChat(@RequestBody ChatRequestDTO request) {
        if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
            return Result.fail("会话ID不能为空");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        try {
            // 1. 检索相关知识
            String context = retrievalService.searchAsContext(request.getMessage());

            // 2. 构建增强提示词
            String augmentedMessage;
            if (!context.isEmpty()) {
                augmentedMessage = context + "\n\n# 用户问题\n" + request.getMessage()
                        + "\n\n请基于上面的参考知识库内容回答用户问题。如果知识库中没有相关信息，请友好地告知用户。";
            } else {
                augmentedMessage = request.getMessage();
            }

            // 3. 调用 Agent（Agent 也可能调用工具，如订单查询）
            String reply = agent.chat(request.getSessionId(), augmentedMessage);
            reply = ReflectionGuard.apply(reply);
            return Result.ok(reply);
        } catch (Exception e) {
            log.error("RAG chat error for session {}", request.getSessionId(), e);
            return Result.fail("AI客服暂时不可用，请稍后再试");
        }
    }
}
