package com.hmdp.agent.graph;

import com.hmdp.agent.graph.state.ReActAgentState;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReAct 图条件边覆盖 — 防止「节点返回 nextNode 但该节点条件边映射缺失」导致运行时
 * GraphRunnerException（历史上 observer→planner 边缺失，确认恢复 replan 时崩溃）。
 */
@SpringBootTest
class AgentGraphEdgesTest {

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @Test
    void observerConditionalEdgeCoversAllItsRoutingTargets() throws Exception {
        Map<String, String> observerMappings = edgeMappings("observer");
        for (String target : new String[]{"executor", "judgeNode", "retryGate", "answer", "observer", "planner"}) {
            assertTrue(observerMappings.containsKey(target),
                    "observer 条件边缺目标: " + target);
        }
        assertEquals("planner", observerMappings.get("planner"));
    }

    @Test
    void plannerEdgeStillCoversItsTargets() throws Exception {
        Map<String, String> plannerMappings = edgeMappings("planner");
        for (String target : new String[]{"executor", "answer", "planner"}) {
            assertTrue(plannerMappings.containsKey(target),
                    "planner 条件边缺目标: " + target);
        }
    }

    /** 反射读取 CompiledGraph 的 edges 字段，取指定节点的条件边目标映射 */
    @SuppressWarnings("unchecked")
    private Map<String, String> edgeMappings(String nodeId) throws Exception {
        Field edgesField = CompiledGraph.class.getDeclaredField("edges");
        edgesField.setAccessible(true);
        Map<String, Object> edges = (Map<String, Object>) edgesField.get(reactGraph);

        Object edgeValue = edges.get(nodeId);
        if (edgeValue == null) throw new AssertionError("graph 无节点: " + nodeId);

        // EdgeValue.value() → EdgeCondition
        Method valueMethod = edgeValue.getClass().getMethod("value");
        Object condition = valueMethod.invoke(edgeValue);

        // EdgeCondition.mappings() → Map<String,String>
        Method mappingsMethod = condition.getClass().getMethod("mappings");
        return (Map<String, String>) mappingsMethod.invoke(condition);
    }
}
