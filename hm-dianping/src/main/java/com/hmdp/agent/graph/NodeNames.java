package com.hmdp.agent.graph;

/**
 * 图节点名常量 —— 替代散落的节点名字符串（addNode / 条件边路由 / nextNode / updateState 共用）。
 *
 * <p>节点返回的 {@code nextNode} 值、{@link GraphConfig} 中条件边映射的 key/value、
 * Controller 里 {@code updateState(..., nodeId)} 与 SSE 节点事件名，都必须使用这里的常量，
 * 避免拼错字符串导致静默改路由。
 */
public final class NodeNames {

    private NodeNames() {}

    /** 上下文管理节点（图入口） */
    public static final String CONTEXT = "context";

    /** 规划节点（纯规划，不执行） */
    public static final String PLANNER = "planner";

    /** Agent 决策节点（ReAct 决策侧） */
    public static final String AGENT = "agent";

    /** 工具执行节点（Action 侧） */
    public static final String TOOLS = "tools";

    /** 回答节点（组装流式 prompt，流式 LLM 调用在 Controller） */
    public static final String ANSWER = "answer";

    /** LangGraph 终止哨兵：AnswerNode 写入 nextNode 表示图结束（answer 节点静态边直达 END） */
    public static final String END = "__END__";
}
