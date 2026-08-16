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
 * Plan-Execute 图条件边覆盖 — 防止「节点返回 nextNode 但该节点条件边映射缺失」导致运行时
 * GraphRunnerException（历史上 observer→planner 边缺失，确认恢复 replan 时崩溃）。
 *
 * <p>当前拓扑（GraphConfig）：
 * <ul>
 *   <li>planner → agent / answer / planner（replan 自环）</li>
 *   <li>agent → tools / answer / agent（自环）/ planner（能力不足 replan）/ context（全新对话重跑图）</li>
 *   <li>tools → agent / answer</li>
 * </ul>
 */
@SpringBootTest
class AgentGraphEdgesTest {

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @Test
    void plannerEdgeCoversItsRoutingTargets() throws Exception {
        Map<String, String> plannerMappings = edgeMappings(NodeNames.PLANNER);
        for (String target : new String[]{NodeNames.AGENT, NodeNames.ANSWER, NodeNames.PLANNER}) {
            assertTrue(plannerMappings.containsKey(target),
                    "planner 条件边缺目标: " + target);
        }
        assertEquals(NodeNames.AGENT, plannerMappings.get(NodeNames.AGENT));
    }

    @Test
    void agentEdgeCoversItsRoutingTargets() throws Exception {
        Map<String, String> agentMappings = edgeMappings(NodeNames.AGENT);
        for (String target : new String[]{NodeNames.TOOLS, NodeNames.ANSWER, NodeNames.AGENT,
                NodeNames.PLANNER, NodeNames.CONTEXT}) {
            assertTrue(agentMappings.containsKey(target),
                    "agent 条件边缺目标: " + target);
        }
        assertEquals(NodeNames.AGENT, agentMappings.get(NodeNames.AGENT));
    }

    @Test
    void toolsEdgeCoversItsRoutingTargets() throws Exception {
        Map<String, String> toolsMappings = edgeMappings(NodeNames.TOOLS);
        for (String target : new String[]{NodeNames.AGENT, NodeNames.ANSWER}) {
            assertTrue(toolsMappings.containsKey(target),
                    "tools 条件边缺目标: " + target);
        }
        assertEquals(NodeNames.AGENT, toolsMappings.get(NodeNames.AGENT));
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
