package com.hmdp.agent;

import com.hmdp.entity.AgentTrace;
import com.hmdp.service.IAgentTraceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AgentTracer：会话结束异步落库 agent_trace（可观测性数据源）。
 */
class AgentTracerTest {

    @Mock private IAgentTraceService agentTraceService;

    @InjectMocks
    private AgentTracer agentTracer;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void record_nullTraceId_skipsSave() {
        agentTracer.record(null, 1L, "q",
                List.of(Map.of("node", "planner")), 10, "completed", null);
        verify(agentTraceService, never()).save(any());
    }

    @Test
    void record_emptySteps_skipsSave() {
        agentTracer.record("t1", 1L, "q", Collections.emptyList(), 10, "completed", null);
        verify(agentTraceService, never()).save(any());
    }

    @Test
    void record_persistsTraceAsync() throws InterruptedException {
        agentTracer.record("t-abc", 2L, "附近有什么火锅",
                List.of(Map.of("node", "planner", "at", 1),
                        Map.of("node", "executor", "tool", "geoSearch", "at", 12)),
                150L, "completed", null);
        // 异步写库，轮询等待完成
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(agentTraceService, times(1)).save(argThat(t -> "t-abc".equals(((AgentTrace) t).getTraceId())));
                break;
            } catch (AssertionError ignored) {
                Thread.sleep(100);
            }
        }
        verify(agentTraceService, times(1)).save(argThat(t -> {
            AgentTrace tr = (AgentTrace) t;
            return "t-abc".equals(tr.getTraceId())
                    && 2L == tr.getUserId()
                    && tr.getNodeCount() == 2
                    && 150L == tr.getTotalMs()
                    && "completed".equals(tr.getStatus())
                    && tr.getStepsJson() != null;
        }));
    }

    @Test
    void record_truncatesLongFields() throws InterruptedException {
        String longQuery = "x".repeat(1500);
        String longErr = "e".repeat(800);
        agentTracer.record("t-long", 1L, longQuery,
                List.of(Map.of("node", "planner")), 5, "error", longErr);
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(agentTraceService, times(1)).save(any());
                break;
            } catch (AssertionError ignored) {
                Thread.sleep(100);
            }
        }
        verify(agentTraceService).save(argThat(t -> {
            AgentTrace tr = (AgentTrace) t;
            assertEquals(1000, tr.getQuery().length());
            assertEquals(500, tr.getErrorMsg().length());
            return true;
        }));
    }
}
