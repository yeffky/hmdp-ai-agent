package com.hmdp.agent.graph;

import com.hmdp.agent.graph.nodes.*;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.ReActStateSerializer;
import com.hmdp.agent.graph.state.StateSchema;
import com.hmdp.agent.memory.context.ContextEditor;
import com.hmdp.agent.memory.context.ContextNode;
import com.hmdp.agent.memory.context.SlidingWindowManager;
import com.hmdp.agent.memory.context.UserStore;
import dev.langchain4j.model.chat.ChatModel;
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

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    private ChatModel model;
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

    @Autowired
    private com.hmdp.agent.skill.SkillRegistry skillRegistry;

    @Autowired
    private com.hmdp.agent.memory.reflection.ReflectionStore reflectionStore;

    @Autowired
    private com.hmdp.agent.memory.context.ProfileExtractor profileExtractor;

    @Autowired
    private ContextEditor contextEditor;

    @Autowired
    private com.hmdp.agent.memory.context.CompressionConfig compressionConfig;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private com.hmdp.agent.config.ToolCacheProperties toolCacheProperties;

    @Autowired
    private com.hmdp.utils.IdObfuscator idObfuscator;

    @Bean("reactGraph")
    public CompiledGraph<ReActAgentState> reactGraph() throws Exception {
        ContextNode contextNode = new ContextNode(windowManager, userStore, chatHistoryRepo, skillRegistry,
                reflectionStore, compressionConfig);
        PlannerNode planner = new PlannerNode(model, maxIterations, toolService, shopTypeProvider, skillRegistry,
                profileExtractor);
        // Plan-Action：agent 只决策，工具执行在 ToolNode（Action）；
        // 充分性判断下沉到 ReAct 决策循环自身（skill SOP「结果不匹配就重查」+ 观察自评）
        ToolExecutor toolExecutor = new ToolExecutor(toolService, stringRedisTemplate, toolCacheProperties);
        AgentNode agent = new AgentNode(model, toolService, maxRetries, skillRegistry, toolExecutor, contextEditor,
                idObfuscator);
        ToolNode toolNode = new ToolNode(toolExecutor);
        AnswerNode answerNode = new AnswerNode(model);

        StateSerializer<ReActAgentState> serializer = new ReActStateSerializer();

        StateGraph<ReActAgentState> graph = new StateGraph<>(
                StateSchema.channelMap(),
                serializer
        );

        graph.addNode(NodeNames.CONTEXT,  AsyncNodeAction.node_async(contextNode))
             .addNode(NodeNames.PLANNER,  AsyncNodeAction.node_async(planner))
             .addNode(NodeNames.AGENT,    AsyncNodeAction.node_async(agent))
             .addNode(NodeNames.TOOLS,    AsyncNodeAction.node_async(toolNode))
             .addNode(NodeNames.ANSWER,   AsyncNodeAction.node_async(answerNode));

        graph.addEdge(GraphDefinition.START, NodeNames.CONTEXT);
        graph.addEdge(NodeNames.CONTEXT, NodeNames.PLANNER);

        // planner：初始规划 → agent（执行计划）；简单/ask_user/cannot_fulfill/迭代超限 → answer；replan 自环
        graph.addConditionalEdges(NodeNames.PLANNER,
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : NodeNames.AGENT),
                Map.of(NodeNames.AGENT, NodeNames.AGENT,
                        NodeNames.ANSWER, NodeNames.ANSWER,
                        NodeNames.PLANNER, NodeNames.PLANNER));

        // agent（决策）：有 tool call → tools；无 tool call / 挂起 / 上限 → answer；恢复后 → agent 自环；
        // 写确认时用户调整参数 → planner 重新规划；用户开启全新对话（非回应挂起）→ context 重新跑图
        graph.addConditionalEdges(NodeNames.AGENT,
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : NodeNames.ANSWER),
                Map.of(NodeNames.TOOLS, NodeNames.TOOLS,
                        NodeNames.ANSWER, NodeNames.ANSWER,
                        NodeNames.AGENT, NodeNames.AGENT,
                        NodeNames.PLANNER, NodeNames.PLANNER,
                        NodeNames.CONTEXT, NodeNames.CONTEXT));

        // tools（Action）：执行完回 agent 继续决策
        graph.addConditionalEdges(NodeNames.TOOLS,
                s -> CompletableFuture.completedFuture(
                        s.nextNode() != null ? s.nextNode() : NodeNames.AGENT),
                Map.of(NodeNames.AGENT, NodeNames.AGENT,
                        NodeNames.ANSWER, NodeNames.ANSWER));

        graph.addEdge(NodeNames.ANSWER, GraphDefinition.END);

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
