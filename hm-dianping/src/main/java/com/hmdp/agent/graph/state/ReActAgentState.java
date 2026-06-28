package com.hmdp.agent.graph.state;

import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

/**
 * ReAct Agent 图状态 — 扩展 LangGraph4j 的 AgentState，
 * 提供类型安全的访问器，配合 StateSchema 中的 Channel 定义使用。
 */
public class ReActAgentState extends AgentState {

    /** LangGraph4j 要求的 Map 构造器（由 AgentStateFactory 调用） */
    public ReActAgentState(Map<String, Object> initData) {
        super(initData);
    }

    // ======== 类型安全的访问器 ========

    public String sessionId() {
        return this.<String>value("sessionId").orElse(null);
    }

    public String userQuery() {
        return this.<String>value("userQuery").orElse(null);
    }

    public String planJson() {
        return this.<String>value("planJson").orElse(null);
    }

    public String finalAnswer() {
        return this.<String>value("finalAnswer").orElse(null);
    }

    public String nextNode() {
        return this.<String>value("nextNode").orElse(null);
    }

    public String compressedSummary() {
        return this.<String>value("compressedSummary").orElse("");
    }

    public String observerFeedback() {
        return this.<String>value("observerFeedback").orElse(null);
    }

    public int iteration() {
        return this.<Number>value("iteration").map(Number::intValue).orElse(0);
    }

    public int toolFailures() {
        return this.<Number>value("toolFailures").map(Number::intValue).orElse(0);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> scratchpad() {
        return this.<Map<String, Object>>value("scratchpad").orElse(new LinkedHashMap<>());
    }

    /**
     * 获取消息历史（List<Map<String,String>>，每个 Map 含 role/content 等字段）。
     * 通过 channel reducer 自动累加。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> messages() {
        return this.<List<Map<String, String>>>value("messages").orElse(new ArrayList<>());
    }

    // ======== 便捷方法 ========

    /** 创建一个 role=user 的消息 Map */
    public static Map<String, String> userMsg(String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "user");
        m.put("content", content);
        return m;
    }

    /** 创建一个 role=assistant 的消息 Map */
    public static Map<String, String> aiMsg(String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        m.put("content", content);
        return m;
    }

    /** 创建一个 role=tool 的消息 Map */
    public static Map<String, String> toolMsg(String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("content", content);
        return m;
    }
}
