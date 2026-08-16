package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.config.ToolCacheProperties;
import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ToolExecutor 工具结果缓存单测 — 相同参数命中不执行工具、非白名单不查缓存。
 * LC4jToolService 为 final 类（Mockito 默认不可 mock）；缓存命中路径不调用工具，故传 null 验证即可。
 */
class ToolExecutorTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        executor = new ToolExecutor(null, redis, new ToolCacheProperties());
    }

    private ReActAgentState state() {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put("messages", new ArrayList<>());
        return new ReActAgentState(init);
    }

    @Test
    void 白名单工具缓存命中_不执行工具() {
        // searchShops 在白名单，Redis 命中缓存 → 直接返回缓存，不碰 toolService（null 若被调用会 NPE，测试即失败）
        when(ops.get(anyString())).thenReturn("[{\"id\":1,\"name\":\"肯德基\"}]");
        ToolExecutionResultMessage r = executor.execute(state(), "searchShops", "{\"typeId\":1}", "c1");
        assertEquals("[{\"id\":1,\"name\":\"肯德基\"}]", r.text());
        assertTrue(!Boolean.TRUE.equals(r.isError()));
        verify(redis, atLeastOnce()).opsForValue(); // 查了缓存
    }

    @Test
    void 非白名单工具_不查缓存() {
        // takeQueueNumber（写操作）不在白名单 → 不查缓存，直接走工具执行（toolService=null → NPE → 错误消息）
        ToolExecutionResultMessage r = executor.execute(state(), "takeQueueNumber", "{}", "c1");
        verify(redis, never()).opsForValue();
        assertTrue(Boolean.TRUE.equals(r.isError()));
    }
}
