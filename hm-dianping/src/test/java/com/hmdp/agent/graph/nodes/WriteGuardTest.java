package com.hmdp.agent.graph.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WriteGuard：写操作工具识别（程序级强制）。
 *
 * <p>确认卡片的展示规范（字段/按钮）由对应 skill 的 references 定义（如 queue skill 的
 * queue-confirmation.md），此处只保留确定性的写工具识别；确认/取消的语义判断由 LLM 完成
 * （AgentNode#resolveWriteIntent）。
 */
class WriteGuardTest {

    @Test
    void identifiesWriteOperations() {
        assertTrue(WriteGuard.isWriteOperation("takeQueueNumber"));
        assertTrue(WriteGuard.isWriteOperation("cancelMyQueue"));
        assertFalse(WriteGuard.isWriteOperation("queryMyQueueStatus"));
        assertFalse(WriteGuard.isWriteOperation("queryShopQueueStatus"));
        assertFalse(WriteGuard.isWriteOperation("geoSearch"));
        assertFalse(WriteGuard.isWriteOperation(null));
    }
}
