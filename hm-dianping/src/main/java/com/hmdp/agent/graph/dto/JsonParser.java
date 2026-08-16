package com.hmdp.agent.graph.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 结构化输出解析：Jackson 反序列化，替代手写 indexOf 抠 JSON。
 */
public final class JsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonParser() {}

    /** 解析 Planner 输出；非法或语义矛盾返回 null */
    public static PlanRequest parsePlan(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            PlanRequest req = MAPPER.readValue(json, PlanRequest.class);
            return validatePlan(req) ? req : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 Executor 输出；非法或语义矛盾返回 null */
    public static ToolCallRequest parseToolCall(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            ToolCallRequest req = MAPPER.readValue(json, ToolCallRequest.class);
            return validateToolCall(req) ? req : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Planner 输出语义校验（strict schema 兜底）：拦截矛盾输出，避免坏数据进入下游。
     * 仅拦截明确矛盾（ask_user 与 cannot_fulfill 互斥）；
     * complex=true 但无 plan 由 {@link PlanRequest#needsExecution()} 语义处理，不在此拦截。
     */
    private static boolean validatePlan(PlanRequest r) {
        return !(hasText(r.getAskUser()) && hasText(r.getCannotFulfill()));
    }

    /** Executor 输出语义校验：ask_user 放行；工具调用必须带非空工具名 */
    private static boolean validateToolCall(ToolCallRequest r) {
        if (r.isAskUserRequest()) {
            return true;
        }
        return r.getTool() != null && !r.getTool().isBlank();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** 对象转 JSON 字符串（如工具参数 Map → 字符串）；失败返回 "{}" */
    public static String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
