package com.hmdp.controller;

import com.hmdp.agent.CustomerServiceAgent;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.guard.ReflectionGuard;
import com.hmdp.dto.ChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.rag.retrieval.RetrievalService;
import com.hmdp.utils.UserHolder;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 聊天控制器 — 三种模式：
 * /chat/rag   = RAG 检索 → Agent（原有模式）
 * /chat/react = ReAct Graph（图引擎 + Checkpoint + 滑动窗口 + 用户画像）
 * /chat/send  = 纯 LLM Agent（无工具）
 *
 * threadId 策略：使用 userId，保证同一用户的跨会话记忆连续性。
 * 匿名用户 fallback 到前端 sessionId。
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
    private CompiledGraph<ReActAgentState> reactGraph;

    /** RAG + Agent 模式（原有 — 不变） */
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

    /** ReAct Graph 模式 — threadId = userId，跨会话记忆 + 用户画像 */
    @PostMapping("/react")
    public Result reactChat(@RequestBody ChatRequestDTO request) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        try {
            // 确定 threadId：优先使用登录用户 ID，匿名用户 fallback 到 sessionId
            Long userId = getCurrentUserId();
            String threadId;
            if (userId != null && userId > 0) {
                threadId = "user:" + userId;
            } else {
                // 匿名用户
                threadId = request.getSessionId() != null && !request.getSessionId().trim().isEmpty()
                        ? "anon:" + request.getSessionId()
                        : "anon:" + UUID.randomUUID().toString().substring(0, 8);
            }

            // 构建初始状态
            Map<String, Object> init = new LinkedHashMap<>();
            init.put("sessionId", threadId);
            init.put("userQuery", request.getMessage());
            init.put("iteration", 0);
            init.put("toolFailures", 0);
            init.put("scratchpad", new LinkedHashMap<>());
            init.put("messages", new ArrayList<>());
            init.put("compressedSummary", "");
            init.put("nextNode", "context");
            if (userId != null) {
                init.put("userId", userId);
            }

            // RunnableConfig: threadId = userId（用户级线程）
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            Optional<ReActAgentState> result = reactGraph.invoke(init, config);
            String answer = result.isPresent()
                    ? result.get().finalAnswer() != null
                        ? result.get().finalAnswer()
                        : "系统处理完成，但未生成回答。"
                    : "系统处理完成，但未生成回答。";

            log.info("ReAct complete: threadId={}, messages={}",
                    threadId,
                    result.map(s -> s.messages().size()).orElse(0));

            return Result.ok(answer);
        } catch (Exception e) {
            log.error("ReAct error for session {}", request.getSessionId(), e);
            return Result.fail("AI客服处理失败: " + e.getMessage());
        }
    }

    /** 获取当前登录用户 ID */
    private Long getCurrentUserId() {
        try {
            if (UserHolder.getUser() != null) {
                return UserHolder.getUser().getId();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
