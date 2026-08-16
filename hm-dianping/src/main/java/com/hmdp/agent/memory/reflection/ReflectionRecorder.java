package com.hmdp.agent.memory.reflection;

import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reflexion 写路径 —— 运行结束后检查失败标记（scratchpad._run_failed），
 * 命中则异步生成教训并入库（不阻塞流式回答）。
 */
@Component
public class ReflectionRecorder {

    private static final Logger log = LoggerFactory.getLogger(ReflectionRecorder.class);

    private static final String RUN_FAILED = "_run_failed";

    @Resource
    private ReflectionStore store;

    @Resource
    private ReflectionGenerator generator;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reflection-writer");
        t.setDaemon(true);
        return t;
    });

    /** 运行结束回调：若本次运行失败，异步生成并保存一条教训。 */
    public void recordIfFailed(ReActAgentState state) {
        if (state == null) return;
        Object failed = state.scratchpad().get(RUN_FAILED);
        if (!Boolean.TRUE.equals(failed)) return;

        final String sessionId = state.sessionId();
        final String query = state.userQuery();
        final String plan = state.plan();
        final String results = formatResults(state.scratchpad());
        final String failureHint = buildFailureHint(state);

        executor.submit(() -> {
            try {
                Reflection r = generator.generate(sessionId, query, plan, results, failureHint);
                if (r != null && r.getLesson() != null && !r.getLesson().isBlank()) {
                    store.save(sessionId, r.getDomain(), query, r.getLesson(), r.getKeywords());
                    log.info("Reflexion saved: domain={}, query={}", r.getDomain(),
                            query != null && query.length() > 40 ? query.substring(0, 40) + "..." : query);
                }
            } catch (Exception e) {
                log.warn("Reflexion record failed: {}", e.getMessage());
            }
        });
    }

    private String formatResults(Map<String, Object> sp) {
        if (sp == null || sp.isEmpty()) return "（无）";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sp.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals(StateKeys.SP_ERROR)) continue;
            String v = e.getValue() != null ? e.getValue().toString() : "";
            if (v.length() > 300) v = v.substring(0, 300) + "...";
            sb.append(k).append(": ").append(v).append("\n");
        }
        return sb.length() == 0 ? "（无）" : sb.toString();
    }

    private String buildFailureHint(ReActAgentState state) {
        StringBuilder sb = new StringBuilder("本次运行未成功满足用户。");
        if (state.emptyResultRetries() >= 2) {
            sb.append("空结果扩大搜索 ≥2 次仍未找到匹配数据。");
        }
        if (state.toolCallCount() >= 6) {
            sb.append("工具调用次数达上限。");
        }
        return sb.toString();
    }
}
