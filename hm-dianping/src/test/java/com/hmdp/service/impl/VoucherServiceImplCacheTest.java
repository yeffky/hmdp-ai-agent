package com.hmdp.service.impl;

import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * VoucherServiceImpl 团购列表缓存写路径失效：save / removeById 后应删除对应店铺的列表缓存。
 */
class VoucherServiceImplCacheTest {

    private VoucherMapper mapper;
    private StringRedisTemplate redis;
    private VoucherServiceImpl svc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(VoucherMapper.class);
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        svc = new VoucherServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(svc, "cacheClient", new CacheClient(redis));
        ReflectionTestUtils.setField(svc, "seckillVoucherService", mock(ISeckillVoucherService.class));
    }

    @Test
    void save_evictsShopVoucherListCache() {
        when(mapper.insert(any())).thenReturn(1);
        Voucher v = new Voucher();
        v.setShopId(10L);
        svc.save(v);
        verify(redis).delete("cache:voucher:list:10");
    }

    @Test
    void removeById_evictsShopVoucherListCache() {
        Voucher existing = new Voucher();
        existing.setId(5L);
        existing.setShopId(10L);
        when(mapper.selectById(5L)).thenReturn(existing);
        when(mapper.deleteById(5L)).thenReturn(1);
        svc.removeById(5L);
        verify(redis).delete("cache:voucher:list:10");
    }

    @Test
    void removeById_missingVoucher_doesNotDeleteCache() {
        when(mapper.selectById(9L)).thenReturn(null);
        when(mapper.deleteById(9L)).thenReturn(1);
        svc.removeById(9L);
        verify(redis, never()).delete(anyString());
    }
}
