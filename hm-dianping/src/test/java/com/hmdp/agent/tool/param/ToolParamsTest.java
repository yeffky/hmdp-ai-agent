package com.hmdp.agent.tool.param;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ToolParams 参数约束校验 — 确定性单测，无需 Spring 上下文。
 */
class ToolParamsTest {

    @Test
    void typeId_accepts1To10() {
        for (int t = 1; t <= 10; t++) {
            int v = t;
            assertDoesNotThrow(() -> ToolParams.typeId(v), "typeId=" + v);
        }
    }

    @Test
    void typeId_rejectsOutOfRange() {
        assertThrows(ToolParamException.class, () -> ToolParams.typeId(0));
        assertThrows(ToolParamException.class, () -> ToolParams.typeId(11));
        assertThrows(ToolParamException.class, () -> ToolParams.typeId(-1));
    }

    @Test
    void lngLat_acceptsValidFuzhou() {
        assertDoesNotThrow(() -> ToolParams.lngLat(119.3, 26.08));
        assertDoesNotThrow(() -> ToolParams.lngLat(-180, -90));
        assertDoesNotThrow(() -> ToolParams.lngLat(180, 90));
    }

    @Test
    void lngLat_rejectsZeroZero() {
        assertThrows(ToolParamException.class, () -> ToolParams.lngLat(0, 0));
    }

    @Test
    void lngLat_rejectsOutOfRange() {
        assertThrows(ToolParamException.class, () -> ToolParams.lngLat(200, 26));
        assertThrows(ToolParamException.class, () -> ToolParams.lngLat(119, 100));
    }

    @Test
    void radius_rejectsOutOfRange() {
        assertThrows(ToolParamException.class, () -> ToolParams.radius(0));
        assertThrows(ToolParamException.class, () -> ToolParams.radius(99));
        assertThrows(ToolParamException.class, () -> ToolParams.radius(50001));
        assertDoesNotThrow(() -> ToolParams.radius(3000));
    }

    @Test
    void status_acceptsNullAnd1To6() {
        assertDoesNotThrow(() -> ToolParams.status(null));
        for (int s = 1; s <= 6; s++) {
            int v = s;
            assertDoesNotThrow(() -> ToolParams.status(v), "status=" + v);
        }
        assertThrows(ToolParamException.class, () -> ToolParams.status(0));
        assertThrows(ToolParamException.class, () -> ToolParams.status(7));
    }

    @Test
    void positive_rejectsNullOrNonPositive() {
        assertThrows(ToolParamException.class, () -> ToolParams.positive(null, "商铺ID"));
        assertThrows(ToolParamException.class, () -> ToolParams.positive(0L, "商铺ID"));
        assertThrows(ToolParamException.class, () -> ToolParams.positive(-5L, "商铺ID"));
        assertDoesNotThrow(() -> ToolParams.positive(1L, "商铺ID"));
    }

    @Test
    void peopleCount_acceptsNullAndPositive() {
        assertDoesNotThrow(() -> ToolParams.peopleCount(null));
        assertDoesNotThrow(() -> ToolParams.peopleCount(2));
        assertThrows(ToolParamException.class, () -> ToolParams.peopleCount(0));
        assertThrows(ToolParamException.class, () -> ToolParams.peopleCount(-1));
    }
}
