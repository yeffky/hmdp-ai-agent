package com.hmdp.task;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 秒杀库存预热（批量版）：1 次批量查 + 1 次 MSET 写 Redis。
 */
class SeckillStockWarmerTest {

    @Mock private IVoucherService voucherService;
    @Mock private ISeckillVoucherService seckillVoucherService;
    @Mock private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private SeckillStockWarmer warmer;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    private QueryChainWrapper<Voucher> stubVoucherChain(List<Voucher> list) {
        @SuppressWarnings("unchecked")
        QueryChainWrapper<Voucher> chain = mock(QueryChainWrapper.class);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.list()).thenReturn(list);
        when(voucherService.query()).thenReturn(chain);
        return chain;
    }

    @Test
    void warmUp_batchSetsRedisStock() {
        stubVoucherChain(List.of(
                new Voucher().setId(1L).setType(1).setStatus(1),
                new Voucher().setId(2L).setType(1).setStatus(1)));
        when(seckillVoucherService.list(any())).thenReturn(List.of(
                new SeckillVoucher().setVoucherId(1L).setStock(30),
                new SeckillVoucher().setVoucherId(2L).setStock(5)));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);

        int n = warmer.warmUp();
        assertEquals(2, n);
        // 单次 MSET 批量写，而非逐条 SET
        verify(vo).multiSet(Map.of("seckill:stock:1", "30", "seckill:stock:2", "5"));
        verify(vo, never()).set(anyString(), anyString());
    }

    @Test
    void warmUp_noSeckillRows_doesNothing() {
        stubVoucherChain(List.of(
                new Voucher().setId(1L).setType(1).setStatus(1)));
        when(seckillVoucherService.list(any())).thenReturn(Collections.emptyList());

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);

        assertEquals(0, warmer.warmUp());
        verify(vo, never()).multiSet(any());
    }
}
