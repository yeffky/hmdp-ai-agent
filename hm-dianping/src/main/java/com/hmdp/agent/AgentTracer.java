package com.hmdp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.AgentTrace;
import com.hmdp.service.IAgentTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Agent 执行轨迹埋点：会话结束后异步写入 agent_trace 表（可观测性后台数据源）。
 * 不阻塞图执行线程；写库失败仅告警，不影响业务。
 */
@Component
public class AgentTracer {

    private static final Logger log = LoggerFactory.getLogger(AgentTracer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private IAgentTraceService agentTraceService;

    /** 专用执行线程池（AgentConfig.agentExecutor）；单测无 Bean 时回退公共池 */
    @Resource(name = "agentExecutor")
    private ExecutorService executor;

    /**
     * 记录一次 Agent 会话轨迹。
     *
     * @param traceId  请求 traceId
     * @param userId   用户 id
     * @param query    用户问题
     * @param steps    节点步骤 [{node, tool, at}]（at 为相对开始毫秒）
     * @param totalMs  图执行总耗时
     * @param status   completed / error
     * @param errorMsg 错误信息
     */
    public void record(String traceId, Long userId, String query,
                       List<Map<String, Object>> steps, long totalMs,
                       String status, String errorMsg) {
        if (traceId == null || traceId.isEmpty() || steps == null || steps.isEmpty()) {
            return;
        }
        Runnable task = () -> {
            try {
                AgentTrace t = new AgentTrace();
                t.setTraceId(traceId);
                t.setUserId(userId);
                t.setQuery(query != null && query.length() > 1000 ? query.substring(0, 1000) : query);
                t.setStepsJson(MAPPER.writeValueAsString(steps));
                t.setNodeCount(steps.size());
                t.setTotalMs(totalMs);
                t.setStatus(status != null ? status : "completed");
                t.setErrorMsg(errorMsg != null && errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
                agentTraceService.save(t);
            } catch (Exception e) {
                log.warn("AgentTracer: failed to persist trace {}: {}", traceId, e.getMessage());
            }
        };
        if (executor != null) {
            CompletableFuture.runAsync(task, executor);
        } else {
            CompletableFuture.runAsync(task);
        }
    }
}
