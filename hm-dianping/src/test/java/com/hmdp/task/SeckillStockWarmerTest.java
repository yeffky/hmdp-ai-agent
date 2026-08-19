package com.hmdp.task;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 秒杀库存预热：1 次 JOIN 批量查 + 仅初始化缺失的 Redis 库存 key。
 */
class SeckillStockWarmerTest {

    @Mock private ISeckillVoucherService seckillVoucherService;
    @Mock private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private SeckillStockWarmer warmer;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    void warmUp_batchSetsRedisStockFromJoinedQuery() {
        when(seckillVoucherService.listActiveForWarmup()).thenReturn(List.of(
                new SeckillVoucher().setVoucherId(1L).setStock(30),
                new SeckillVoucher().setVoucherId(2L).setStock(5)));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);
        when(vo.setIfAbsent(anyString(), anyString())).thenReturn(true);

        int n = warmer.warmUp();
        assertEquals(2, n);
        // 只初始化缺失 key，不能覆盖秒杀过程中 Redis 的实时库存
        verify(vo).setIfAbsent("seckill:stock:1", "30");
        verify(vo).setIfAbsent("seckill:stock:2", "5");
        verify(vo, never()).multiSet(anyMap());
    }

    @Test
    void warmUp_noSeckillRows_doesNothing() {
        when(seckillVoucherService.listActiveForWarmup()).thenReturn(Collections.emptyList());

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);

        assertEquals(0, warmer.warmUp());
        verify(vo, never()).setIfAbsent(anyString(), anyString());
    }
}
