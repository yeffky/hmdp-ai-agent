package com.hmdp.agent.graph.state;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * ReActAgentState 轮次计数器：counters Map 读取 + 旧 checkpoint 顶层标量兼容 + 副本语义。
 */
class ReActAgentStateTest {

    @Test
    void countersMap_readsCounters() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put(StateKeys.TOOL_CALL_COUNT, 3);
        counters.put(StateKeys.ITERATION, 2);
        Map<String, Object> init = new LinkedHashMap<>();
        init.put(StateKeys.COUNTERS, counters);

        ReActAgentState state = new ReActAgentState(init);
        assertEquals(3, state.toolCallCount());
        assertEquals(2, state.iteration());
    }

    @Test
    void legacyTopLevelScalars_fallback() {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put("toolCallCount", 5);   // 旧 checkpoint 顶层标量（无 counters map）
        ReActAgentState state = new ReActAgentState(init);
        assertEquals(5, state.toolCallCount());
        assertEquals(0, state.iteration()); // 缺失键回退 0
    }

    @Test
    void counters_returnsCopy_notShared() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put(StateKeys.TOOL_CALL_COUNT, 1);
        Map<String, Object> init = new LinkedHashMap<>();
        init.put(StateKeys.COUNTERS, counters);

        ReActAgentState state = new ReActAgentState(init);
        Map<String, Object> copy = state.counters();
        copy.put(StateKeys.TOOL_CALL_COUNT, 99);

        assertEquals(1, state.toolCallCount()); // 原状态不受影响
        assertNotSame(counters, copy);
    }
}
