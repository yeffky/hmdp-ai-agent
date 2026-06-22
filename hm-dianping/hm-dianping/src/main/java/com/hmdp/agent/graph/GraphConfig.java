package com.hmdp.agent.graph;

import com.hmdp.agent.graph.nodes.*;
import com.hmdp.agent.tool.KnowledgeRetrievalTool;
import com.hmdp.agent.tool.OrderQueryTool;
import com.hmdp.agent.tool.graph.DynamicQueryTool;
import com.hmdp.agent.tool.graph.GeoSearchTool;
import com.hmdp.agent.tool.graph.ShopDetailTool;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LangGraph4j StateGraph 装配 —— ReAct Agent 编排。
 */
@Configuration
public class GraphConfig {

    @Value("${agent.graph.max-iterations:8}")
    private int maxIterations;

    @Resource
    private OpenAiChatModel model;

    @Resource
    private DynamicQueryTool dynamicQueryTool;
    @Resource
    private GeoSearchTool geoSearchTool;
    @Resource
    private ShopDetailTool shopDetailTool;
    @Resource
    private KnowledgeRetrievalTool retrievalTool;
    @Resource
    private OrderQueryTool orderTool;

    @Bean("reactGraph")
    public CompiledGraph<AgentState> reactGraph() throws Exception {
        PlannerNode planner = new PlannerNode(model, maxIterations);
        ExecutorNode executor = new ExecutorNode(model, retrievalTool, orderTool,
                dynamicQueryTool, geoSearchTool, shopDetailTool);
        ObserverNode observer = new ObserverNode(model, maxIterations);
        AnswerNode answerNode = new AnswerNode(model);

        StateGraph<AgentState> graph = new StateGraph<>(stateMap -> new AgentState(stateMap));
        graph.addNode("planner", s -> runNode(s, planner))
             .addNode("executor", s -> runNode(s, executor))
             .addNode("observer", s -> runNode(s, observer))
             .addNode("answer", s -> runNode(s, answerNode))
             .addEdge("planner", "executor")
             .addEdge("executor", "observer")
             .addConditionalEdges("observer",
                     s -> CompletableFuture.completedFuture(
                             s.value("nextNode").orElse("answer").toString()),
                     Map.of("plan", "planner", "answer", "answer"))
             .addEdge("answer", StateGraph.END);
        graph.setEntryPoint("planner");
        return graph.compile();
    }

    /** 执行一个 Node，将 Map 状态转换回 AgentState，执行后将结果增量写入 State */
    private CompletableFuture<Map<String, Object>> runNode(AgentState state, Object node) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (node instanceof PlannerNode) {
                    return ((PlannerNode) node).execute(toMap(state));
                } else if (node instanceof ExecutorNode) {
                    return ((ExecutorNode) node).execute(toMap(state));
                } else if (node instanceof ObserverNode) {
                    return ((ObserverNode) node).execute(toMap(state));
                } else if (node instanceof AnswerNode) {
                    return ((AnswerNode) node).execute(toMap(state));
                }
            } catch (Exception e) {
                return Map.of("nextNode", "answer", "finalAnswer", "处理出错: " + e.getMessage());
            }
            return Map.of("nextNode", "answer");
        });
    }

    /** AgentState → Map（LangGraph4j 内部用 Map 存状态） */
    private Map<String, Object> toMap(AgentState s) {
        return s.data();
    }
}
