package com.hmdp.agent.graph.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 结构化输出解析：Jackson 反序列化，替代手写 indexOf 抠 JSON。
 */
public final class JsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonParser() {}

    /** 解析 Planner 输出；非法返回 null */
    public static PlanRequest parsePlan(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, PlanRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 Executor 输出；非法返回 null */
    public static ToolCallRequest parseToolCall(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, ToolCallRequest.class);
        } catch (Exception e) {
            return null;
        }
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
