package com.hmdp.agent.graph;

import com.hmdp.agent.graph.nodes.*;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.ReActStateSerializer;
import com.hmdp.agent.graph.state.StateSchema;
import com.hmdp.agent.memory.context.ContextNode;
import com.hmdp.agent.memory.context.SlidingWindowManager;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LangGraph4j StateGraph 装配 —— ReAct Agent 编排。
 *
 * Checkpoint 存储策略：
 * - PG 可用时使用 PostgresSaver
 * - PG 不可用时使用 MemorySaver（进程内，不持久化）
 */
@Configuration
public class GraphConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphConfig.class);

    @Value("${agent.graph.max-iterations:8}")
    private int maxIterations;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    private OpenAiChatModel model;
    @Autowired
    private LC4jToolService toolService;
    @Autowired
    private SlidingWindowManager windowManager;

    @Bean("reactGraph")
    public CompiledGraph<ReActAgentState> reactGraph() throws Exception {
        ContextNode contextNode = new ContextNode(windowManager);
        PlannerNode planner = new PlannerNode(model, maxIterations);
        ExecutorNode executor = new ExecutorNode(model, toolService);
        ObserverNode observer = new ObserverNode(model, maxIterations);
        AnswerNode answerNode = new AnswerNode(model);

        StateSerializer<ReActAgentState> serializer = new ReActStateSerializer();

        StateGraph<ReActAgentState> graph = new StateGraph<>(
                StateSchema.channelMap(),
                serializer
        );

        graph.addNode("context",  AsyncNodeAction.node_async(contextNode))
             .addNode("planner",  AsyncNodeAction.node_async(planner))
             .addNode("executor", AsyncNodeAction.node_async(executor))
             .addNode("observer", AsyncNodeAction.node_async(observer))
             .addNode("answer",   AsyncNodeAction.node_async(answerNode));

        graph.addEdge(GraphDefinition.START, "context");
        graph.addEdge("context", "planner");
        graph.addEdge("planner", "executor");

        // executor 后可直达 answer（如 ask_user），也可走 observer 正常评估
        graph.addConditionalEdges("executor",
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null && "answer".equals(s.nextNode())
                                ? "answer" : "observer"),
                Map.of("observer", "observer", "answer", "answer"));

        graph.addConditionalEdges("observer",
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : "answer"),
                Map.of("plan", "planner", "answer", "answer", "ask_user", "answer"));

        graph.addEdge("answer", GraphDefinition.END);

        // ======== Checkpoint Saver ========
        BaseCheckpointSaver saver = createCheckpointSaver(serializer);

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        log.info("ReAct Graph compiled with {}", saver.getClass().getSimpleName());
        return graph.compile(compileConfig);
    }

    private BaseCheckpointSaver createCheckpointSaver(StateSerializer<ReActAgentState> serializer) {
        if (postgresDataSource != null) {
            try {
                PostgresSaver saver = PostgresSaver.builder()
                        .datasource(postgresDataSource)
                        .stateSerializer(serializer)
                        .createTables(true)
                        .build();
                log.info("Using PostgresSaver — shared checkpoint across all instances");
                return saver;
            } catch (Exception e) {
                log.error("PostgresSaver init failed — aborting startup (load-balance requires PG)", e);
                throw new RuntimeException("PostgresSaver unavailable, cannot start in load-balance mode", e);
            }
        }
        log.warn("PostgreSQL not configured — using MemorySaver (single-instance dev mode only)");
        return new MemorySaver();
    }
}
