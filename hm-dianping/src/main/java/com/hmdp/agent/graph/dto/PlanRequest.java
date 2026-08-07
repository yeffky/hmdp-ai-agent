package com.hmdp.agent.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Planner 输出（结构化）：初始规划 / 重规划 / ask_user / cannot_fulfill。
 * 由 LLM 输出 JSON，经 Jackson 反序列化。
 */
public class PlanRequest {

    /** 用户意图一句话 */
    private String intent;

    /** 是否需要工具：false=直接回答（闲聊/常识） */
    private Boolean complex;

    /** 执行计划步骤 */
    private List<String> plan;

    /** ask_user：需要用户补充的信息 */
    @JsonProperty("ask_user")
    private String askUser;

    /** cannot_fulfill：能力边界说明 */
    @JsonProperty("cannot_fulfill")
    private String cannotFulfill;

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public Boolean getComplex() { return complex; }
    public void setComplex(Boolean complex) { this.complex = complex; }
    public List<String> getPlan() { return plan; }
    public void setPlan(List<String> plan) { this.plan = plan; }
    public String getAskUser() { return askUser; }
    public void setAskUser(String askUser) { this.askUser = askUser; }
    public String getCannotFulfill() { return cannotFulfill; }
    public void setCannotFulfill(String cannotFulfill) { this.cannotFulfill = cannotFulfill; }

    /** complex=true 且有 plan */
    public boolean needsExecution() {
        return Boolean.TRUE.equals(complex) && plan != null && !plan.isEmpty();
    }
}
