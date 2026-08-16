package com.hmdp.agent.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checkpoint TTL 归档：释放闲置线程 + 清理过期 checkpoint 行。
 * 纯单元测试（mock JdbcTemplate），验证 SQL 与参数正确下发。
 */
class CheckpointGcSchedulerTest {

    private JdbcTemplate pg;
    private CheckpointGcScheduler scheduler;

    @BeforeEach
    void setUp() {
        pg = mock(JdbcTemplate.class);
        when(pg.update(anyString(), anyInt())).thenReturn(2, 3);
        scheduler = new CheckpointGcScheduler(pg);
        // 单元测试无 Spring @Value 注入，手动设置阈值
        ReflectionTestUtils.setField(scheduler, "inactiveDays", 7);
        ReflectionTestUtils.setField(scheduler, "purgeDays", 30);
    }

    @Test
    void gc_releasesStaleThreadsAndPurgesOldCheckpoints() {
        scheduler.gc();
        // 两步：先释放线程（UPDATE lg4jthread），再清理 checkpoint（DELETE lg4jcheckpoint）
        verify(pg, times(2)).update(anyString(), anyInt());
        verify(pg).update(contains("lg4jthread"), eq(7));
        verify(pg).update(contains("lg4jcheckpoint"), eq(30));
    }

    @Test
    void gc_pgFailure_isSwallowed() {
        when(pg.update(anyString(), anyInt())).thenThrow(new RuntimeException("connection refused"));
        // PG 不可用只告警不抛出（checkpoint 写入不受影响）
        scheduler.gc();
        verify(pg, times(1)).update(anyString(), anyInt());
    }
}
