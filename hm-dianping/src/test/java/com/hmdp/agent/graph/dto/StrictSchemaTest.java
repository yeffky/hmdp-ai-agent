package com.hmdp.agent.graph.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 中间层输出 strict schema 语义校验：拦截矛盾输出，避免坏数据进入下游。
 */
class StrictSchemaTest {

    // ---------- Planner 输出 ----------

    @Test
    void parsePlan_ok_complexWithPlan() {
        assertNotNull(JsonParser.parsePlan("{\"intent\":\"找火锅\",\"complex\":true,\"plan\":[\"第1步：搜索\"]}"));
    }

    @Test
    void parsePlan_ok_simple() {
        assertNotNull(JsonParser.parsePlan("{\"intent\":\"你好\",\"complex\":false}"));
    }

    @Test
    void parsePlan_ok_askUser() {
        assertNotNull(JsonParser.parsePlan("{\"intent\":\"排队\",\"ask_user\":\"请告诉我是哪家店\"}"));
    }

    @Test
    void parsePlan_reject_askUserAndCannotFulfillBothSet() {
        assertNull(JsonParser.parsePlan("{\"intent\":\"x\",\"ask_user\":\"请补充\",\"cannot_fulfill\":\"做不到\"}"));
    }

    @Test
    void parsePlan_reject_malformedJson() {
        assertNull(JsonParser.parsePlan("{not-json"));
        assertNull(JsonParser.parsePlan(""));
    }

    // ---------- Executor 输出 ----------

    @Test
    void parseToolCall_ok_toolWithArgs() {
        assertNotNull(JsonParser.parseToolCall("{\"tool\":\"geoSearch\",\"args\":{\"type\":\"美食\"}}"));
    }

    @Test
    void parseToolCall_ok_askUser() {
        assertNotNull(JsonParser.parseToolCall("{\"ask_user\":true,\"missing\":\"请补充信息\"}"));
    }

    @Test
    void parseToolCall_reject_blankTool() {
        // 非 ask_user 但工具名为空白 → 无效
        assertNull(JsonParser.parseToolCall("{\"tool\":\"  \",\"args\":{}}"));
    }

    @Test
    void parseToolCall_reject_malformedJson() {
        assertNull(JsonParser.parseToolCall("{oops"));
    }
}
