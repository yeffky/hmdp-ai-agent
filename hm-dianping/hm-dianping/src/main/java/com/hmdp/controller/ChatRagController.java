package com.hmdp.controller;

import com.hmdp.agent.CustomerServiceAgent;
import com.hmdp.agent.guard.ReflectionGuard;
import com.hmdp.dto.ChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.rag.retrieval.RetrievalService;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天控制器 — 三种模式：
 * /chat/rag   = RAG 检索 → Agent（原有模式）
 * /chat/react = ReAct Graph（新图引擎，多步推理）
 * /chat/send  = 纯 LLM Agent（无工具）
 */
@RestController
@RequestMapping("/chat")
public class ChatRagController {

    private static final Logger log = LoggerFactory.getLogger(ChatRagController.class);

    @Resource
    private CustomerServiceAgent agent;

    @Resource
    private RetrievalService retrievalService;

    @Resource(name = "reactGraph")
    private CompiledGraph reactGraph;

    /** RAG + Agent 模式（原有） */
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

    /** ReAct Graph 模式（新） */
    @PostMapping("/react")
    public Result reactChat(@RequestBody ChatRequestDTO request) {
        if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
            return Result.fail("会话ID不能为空");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        try {
            // 构建初始 State
            Map<String, Object> init = new LinkedHashMap<>();
            init.put("sessionId", request.getSessionId());
            init.put("userQuery", request.getMessage());
            init.put("iteration", 0);
            init.put("toolFailures", 0);
            init.put("scratchpad", new LinkedHashMap<>());
            init.put("nextNode", "planner");

            java.util.Optional<AgentState> result = reactGraph.invoke(init);
            String answer = result.isPresent()
                    ? result.get().value("finalAnswer").orElse("系统处理完成，但未生成回答。").toString()
                    : "系统处理完成，但未生成回答。";
            return Result.ok(answer);
        } catch (Exception e) {
            log.error("ReAct error for session {}", request.getSessionId(), e);
            return Result.fail("AI客服处理失败: " + e.getMessage());
        }
    }
}
