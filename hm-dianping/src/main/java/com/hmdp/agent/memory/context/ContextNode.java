package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.state.ReActAgentState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 上下文管理节点 — 图入口，在每次请求开始时执行滑动窗口/压缩。
 * 循环边回到 planner 而非 context，避免单次请求内重复压缩。
 */
public class ContextNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ContextNode.class);

    private final SlidingWindowManager windowManager;

    public ContextNode(SlidingWindowManager windowManager) {
        this.windowManager = windowManager;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        log.debug("ContextNode: managing context for session {}", state.sessionId());
        Map<String, Object> updates = windowManager.manageContext(state);
        // 确保路由到 planner
        if (!updates.containsKey("nextNode")) {
            updates.put("nextNode", "planner");
        }
        return updates;
    }
}
