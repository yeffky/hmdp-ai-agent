package com.hmdp.agent.graph;

import com.hmdp.agent.graph.nodes.*;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.ReActStateSerializer;
import com.hmdp.agent.graph.state.StateSchema;
import com.hmdp.agent.memory.context.ContextNode;
import com.hmdp.agent.memory.context.SlidingWindowManager;
import com.hmdp.agent.memory.context.UserStore;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import com.hmdp.agent.graph.checkpoint.DeltaPostgresSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
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

    @Value("${agent.graph.max-retries:3}")
    private int maxRetries;

    @Value("${agent.graph.llm-error-classify:true}")
    private boolean llmErrorClassify;

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    private OpenAiChatModel model;
    @Autowired
    private LC4jToolService toolService;
    @Autowired
    private SlidingWindowManager windowManager;
    @Autowired
    private UserStore userStore;
    @Autowired
    private com.hmdp.repository.ChatHistoryRepository chatHistoryRepo;

    @Autowired
    private com.hmdp.agent.tool.ShopTypeProvider shopTypeProvider;

    @Bean("reactGraph")
    public CompiledGraph<ReActAgentState> reactGraph() throws Exception {
        ContextNode contextNode = new ContextNode(windowManager, userStore, chatHistoryRepo);
        PlannerNode planner = new PlannerNode(model, maxIterations, toolService, shopTypeProvider);
        ExecutorNode executor = new ExecutorNode(model, toolService, llmErrorClassify, shopTypeProvider);
        ObserverNode observer = new ObserverNode(model, maxIterations);
        JudgeNode judgeNode = new JudgeNode(model);
        AnswerNode answerNode = new AnswerNode(model);
        RetryGateNode retryGate = new RetryGateNode(maxRetries);

        StateSerializer<ReActAgentState> serializer = new ReActStateSerializer();

        StateGraph<ReActAgentState> graph = new StateGraph<>(
                StateSchema.channelMap(),
                serializer
        );

        graph.addNode("context",  AsyncNodeAction.node_async(contextNode))
             .addNode("planner",  AsyncNodeAction.node_async(planner))
             .addNode("executor", AsyncNodeAction.node_async(executor))
             .addNode("observer", AsyncNodeAction.node_async(observer))
             .addNode("judgeNode", AsyncNodeAction.node_async(judgeNode))
             .addNode("answer",   AsyncNodeAction.node_async(answerNode))
             .addNode("retryGate", AsyncNodeAction.node_async(retryGate));

        graph.addEdge(GraphDefinition.START, "context");
        graph.addEdge("context", "planner");
        graph.addConditionalEdges("planner",
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : "executor"),
                Map.of("executor", "executor",
                        "answer", "answer",
                        "planner", "planner"));

        // executor 条件路由：observer（正常）/ retryGate（可重试）/ judgeNode（参数缺失等）/ answer（致命）
        graph.addConditionalEdges("executor",
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : "observer"),
                Map.of("observer", "observer",
                        "retryGate", "retryGate",
                        "judgeNode", "judgeNode",
                        "answer", "answer",
                        "executor", "executor"));

        // retryGate 条件路由：executor（继续重试）/ answer（重试耗尽）
        graph.addConditionalEdges("retryGate",
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : "answer"),
                Map.of("executor", "executor",
                        "answer", "answer",
                        "retryGate", "retryGate"));

        // observer 路由：executor（继续执行）/ judgeNode（判断充分性）/ retryGate（错误重试）/ answer（透传）
        // observer 自环：用于 checkpoint resume 时直接回到 observer 自身
        // observer→planner：确认恢复时用户回复与上下文不匹配，ObserverNode 返回 nextNode=planner 要求重新规划
        graph.addConditionalEdges("observer",
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : "judgeNode"),
                Map.of("executor", "executor",
                        "judgeNode", "judgeNode",
                        "retryGate", "retryGate",
                        "answer", "answer",
                        "observer", "observer",
                        "planner", "planner"));

        // judgeNode 两路路由：answer（充分/需用户补充）/ planner（不足，重新规划）
        graph.addConditionalEdges("judgeNode",
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : "answer"),
                Map.of("answer", "answer",
                        "planner", "planner",
                        "judgeNode", "judgeNode"));

        graph.addEdge("answer", GraphDefinition.END);

        // ======== Checkpoint Saver ========
        BaseCheckpointSaver saver = createCheckpointSaver(serializer);

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        log.info("ReAct Graph compiled with {}", saver.getClass().getSimpleName());
        return graph.compile(compileConfig);
    }

    private BaseCheckpointSaver createCheckpointSaver(StateSerializer<ReActAgentState> serializer) throws Exception {
        PostgresSaver.Builder builder = PostgresSaver.builder()
                .datasource(postgresDataSource)
                .stateSerializer(serializer)
                .createTables(true);
        DeltaPostgresSaver saver = new DeltaPostgresSaver(builder, serializer);
        log.info("Using DeltaPostgresSaver — delta storage at {} snapshot_frequency", 5);
        return saver;
    }
}
