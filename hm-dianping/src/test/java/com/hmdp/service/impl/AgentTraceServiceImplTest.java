package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentTrace;
import com.hmdp.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Agent 轨迹查询：分页列表 + 按 traceId 详情。
 */
class AgentTraceServiceImplTest {

    @Mock private AgentTraceMapper agentTraceMapper;

    @Spy
    @InjectMocks
    private AgentTraceServiceImpl agentTraceService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(agentTraceService, "baseMapper", agentTraceMapper);
    }

    @SuppressWarnings("unchecked")
    private QueryChainWrapper<AgentTrace> stubQuery(List<AgentTrace> records) {
        QueryChainWrapper<AgentTrace> chain = mock(QueryChainWrapper.class);
        when(chain.orderByDesc(anyString())).thenReturn(chain);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.eq(anyBoolean(), anyString(), any())).thenReturn(chain);
        when(chain.last(anyString())).thenReturn(chain);
        Page<AgentTrace> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(records.size());
        when(chain.page(any(Page.class))).thenReturn(page);
        when(chain.one()).thenReturn(records.isEmpty() ? null : records.get(0));
        doReturn(chain).when(agentTraceService).query();
        return chain;
    }

    @Test
    void listTraces_returnsPagedList() {
        AgentTrace t = new AgentTrace().setTraceId("abc").setQuery("附近火锅").setNodeCount(4).setTotalMs(120L);
        stubQuery(Collections.singletonList(t));

        Result r = agentTraceService.listTraces(1L, 1, 20);
        assertTrue(r.getSuccess());
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals(1, ((List<?>) data.get("list")).size());
        assertFalse((Boolean) data.get("hasMore"));
        assertEquals("abc", ((List<AgentTrace>) data.get("list")).get(0).getTraceId());
    }

    @Test
    void traceDetail_findsByTraceId() {
        stubQuery(Collections.singletonList(new AgentTrace().setTraceId("xyz")));
        Result r = agentTraceService.traceDetail(1L, "xyz");
        assertTrue(r.getSuccess());
        AgentTrace t = (AgentTrace) r.getData();
        assertNotNull(t);
        assertEquals("xyz", t.getTraceId());
    }

    @Test
    void traceDetail_emptyTraceId_fails() {
        assertFalse(agentTraceService.traceDetail(1L, null).getSuccess());
        assertFalse(agentTraceService.traceDetail(1L, "").getSuccess());
    }

    @Test
    void traceDetail_notFound_returnsNullData() {
        stubQuery(Collections.emptyList());
        Result r = agentTraceService.traceDetail(1L, "missing");
        assertTrue(r.getSuccess());
        assertNull(r.getData());
    }
}
