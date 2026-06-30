package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.error.ErrorCategory;
import com.hmdp.agent.graph.state.ReActAgentState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 重试门控节点 —— 评估重试计数，决定继续重试还是升级为 FATAL。
 *
 * <p>参照 LangGraph 的 {@code retryPolicy}：指数退避 + 随机抖动，重试耗尽后
 * 通过 {@code errorHandler} 路由到降级节点。
 */
public class RetryGateNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(RetryGateNode.class);
    private final int maxRetries;

    public RetryGateNode(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        int retryCount = state.retryCount();
        String toolName = state.lastToolName();
        String errorCategory = state.errorCategory();

        if (retryCount >= maxRetries) {
            log.warn("Retry exhausted for tool {}: {} attempts", toolName, retryCount);
            return Map.of(
                    "retryCount", 0,
                    "errorCategory", ErrorCategory.FATAL.name(),
                    "lastToolName", "",
                    "lastToolArgs", "",
                    "finalAnswer", "抱歉，" + toolName + " 多次重试后仍然失败，请稍后重试。",
                    "nextNode", "answer"
            );
        }

        long backoff = Math.min(
                2000L * (1L << retryCount) + ThreadLocalRandom.current().nextLong(0, 1000),
                15000L
        );
        log.info("Retry #{}/{} for tool {}, sleeping {}ms", retryCount + 1, maxRetries, toolName, backoff);
        Thread.sleep(backoff);

        return Map.of(
                "retryCount", retryCount + 1,
                "nextNode", "executor"
        );
    }
}
