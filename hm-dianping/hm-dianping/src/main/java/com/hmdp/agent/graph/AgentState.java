package com.hmdp.agent.graph;

import java.util.*;

/**
 * ReAct Agent 共享状态 — 在 Graph Nodes 之间流转。
 */
public class AgentState {

    private String sessionId;
    private String userQuery;
    private List<Map<String, String>> history = new ArrayList<>();
    private String planJson;                          // Planner 输出的 JSON 子任务
    private Map<String, Object> scratchpad = new LinkedHashMap<>(); // 工具执行中间结果
    private int iteration;
    private int toolFailures;
    private String nextNode;                           // Observer 设置的路由目标
    private String finalAnswer;

    // LangGraph4j 要求的 copy 构造
    public AgentState() {}

    public AgentState(AgentState other) {
        this.sessionId = other.sessionId;
        this.userQuery = other.userQuery;
        this.history = new ArrayList<>(other.history);
        this.planJson = other.planJson;
        this.scratchpad = new LinkedHashMap<>(other.scratchpad);
        this.iteration = other.iteration;
        this.toolFailures = other.toolFailures;
        this.nextNode = other.nextNode;
        this.finalAnswer = other.finalAnswer;
    }

    // ======== Getters / Setters ========

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserQuery() { return userQuery; }
    public void setUserQuery(String userQuery) { this.userQuery = userQuery; }

    public List<Map<String, String>> getHistory() { return history; }
    public void setHistory(List<Map<String, String>> history) { this.history = history; }

    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }

    public Map<String, Object> getScratchpad() { return scratchpad; }
    public void setScratchpad(Map<String, Object> scratchpad) { this.scratchpad = scratchpad; }

    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = iteration; }

    public int getToolFailures() { return toolFailures; }
    public void setToolFailures(int toolFailures) { this.toolFailures = toolFailures; }

    public String getNextNode() { return nextNode; }
    public void setNextNode(String nextNode) { this.nextNode = nextNode; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
}
