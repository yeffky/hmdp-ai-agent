package com.hmdp.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class XxlJobHandlersTest {

    @Test
    void ragScanIsSkippedWhenDocumentWatcherIsDisabled() {
        assertDoesNotThrow(() -> new XxlJobHandlers().ragDocFullScan());
    }
}
