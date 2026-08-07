package com.hmdp.agent.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Executor 输出（结构化）：工具调用 / ask_user。
 * 由 LLM 输出 JSON，经 Jackson 反序列化。
 */
public class ToolCallRequest {

    /** 要调用的工具名 */
    private String tool;

    /** 工具参数 */
    private Map<String, Object> args;

    /** ask_user：需要用户提供信息 */
    @JsonProperty("ask_user")
    private Boolean askUser;

    /** 给用户看的提示文本（ask_user 时） */
    private String missing;

    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public Map<String, Object> getArgs() { return args; }
    public void setArgs(Map<String, Object> args) { this.args = args; }
    public Boolean getAskUser() { return askUser; }
    public void setAskUser(Boolean askUser) { this.askUser = askUser; }
    public String getMissing() { return missing; }
    public void setMissing(String missing) { this.missing = missing; }

    /** 是否命中 ask_user 分支 */
    public boolean isAskUserRequest() {
        return Boolean.TRUE.equals(askUser)
                || tool == null || tool.isEmpty() || "ask_user".equals(tool);
    }
}
