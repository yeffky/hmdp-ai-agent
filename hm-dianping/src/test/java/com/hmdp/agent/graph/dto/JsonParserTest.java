package com.hmdp.agent.graph.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonParser 结构化解析 — 覆盖 Planner/Executor 输出的合法与非法 JSON。
 */
class JsonParserTest {

    // ============ parseToolCall ============

    @Test
    void parseToolCall_valid() {
        ToolCallRequest r = JsonParser.parseToolCall(
                "{\"tool\": \"geoSearch\", \"args\": {\"typeId\": 1, \"radius\": 3000}}");
        assertTrue(r != null);
        assertEquals("geoSearch", r.getTool());
        assertEquals(1, ((Number) r.getArgs().get("typeId")).intValue());
        assertEquals(3000, ((Number) r.getArgs().get("radius")).intValue());
        assertFalse(r.isAskUserRequest());
    }

    @Test
    void parseToolCall_askUser() {
        ToolCallRequest r = JsonParser.parseToolCall(
                "{\"ask_user\": true, \"missing\": \"请提供用餐人数\"}");
        assertTrue(r.isAskUserRequest());
        assertEquals("请提供用餐人数", r.getMissing());
    }

    @Test
    void parseToolCall_askUserByEmptyTool() {
        ToolCallRequest r = JsonParser.parseToolCall("{\"tool\": \"\", \"args\": {}}");
        assertTrue(r.isAskUserRequest());
    }

    @Test
    void parseToolCall_invalidReturnsNull() {
        assertNull(JsonParser.parseToolCall("不是JSON"));
        assertNull(JsonParser.parseToolCall(""));
        assertNull(JsonParser.parseToolCall(null));
        assertNull(JsonParser.parseToolCall("[1,2,3]"));
    }

    // ============ parsePlan ============

    @Test
    void parsePlan_complex() {
        PlanRequest p = JsonParser.parsePlan(
                "{\"intent\": \"查火锅\", \"complex\": true, \"plan\": [\"第1步：geoSearch\"]}");
        assertTrue(p != null);
        assertTrue(p.needsExecution());
        assertEquals(1, p.getPlan().size());
    }

    @Test
    void parsePlan_simple() {
        PlanRequest p = JsonParser.parsePlan("{\"intent\": \"你好\", \"complex\": false}");
        assertTrue(p != null);
        assertFalse(p.needsExecution());
    }

    @Test
    void parsePlan_askUser() {
        PlanRequest p = JsonParser.parsePlan("{\"ask_user\": \"请提供位置\"}");
        assertTrue(p != null);
        assertEquals("请提供位置", p.getAskUser());
    }

    @Test
    void parsePlan_cannotFulfill() {
        PlanRequest p = JsonParser.parsePlan("{\"cannot_fulfill\": \"暂不支持退款\"}");
        assertTrue(p != null);
        assertEquals("暂不支持退款", p.getCannotFulfill());
    }

    @Test
    void parsePlan_complexWithoutPlanNotExecutable() {
        PlanRequest p = JsonParser.parsePlan("{\"intent\": \"x\", \"complex\": true}");
        assertTrue(p != null);
        assertFalse(p.needsExecution());
    }

    @Test
    void parsePlan_invalidReturnsNull() {
        assertNull(JsonParser.parsePlan("{broken"));
        assertNull(JsonParser.parsePlan(""));
        assertNull(JsonParser.parsePlan(null));
    }

    // ============ toJson ============

    @Test
    void toJson_map() {
        String json = JsonParser.toJson(Map.of("typeId", 1, "radius", 3000));
        assertTrue(json.contains("\"typeId\":1"), json);
        assertTrue(json.contains("\"radius\":3000"), json);
    }

    @Test
    void toJson_nullOrFailReturnsEmptyBraces() {
        assertEquals("{}", JsonParser.toJson(null));
        // getter 抛异常 → Jackson 抛 JsonMappingException → 兜底 {}
        assertEquals("{}", JsonParser.toJson(new BoomBean()));
    }

    private static class BoomBean {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }
}
