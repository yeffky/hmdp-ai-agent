package com.hmdp.utils;

import com.hmdp.entity.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CacheClient.queryListWithPassThrough（列表 Cache-Aside + 空值防穿透）单元测试。
 */
class CacheClientListTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private CacheClient client;

    private static final String KEY = "cache:shop:list:1:0::1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        client = new CacheClient(redis);
    }

    private Shop shop(long id, String name) {
        Shop s = new Shop();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private String shopJson(long id, String name) {
        return "[{\"id\":" + id + ",\"name\":\"" + name + "\"}]";
    }

    @Test
    void hit_returnsListFromRedis_withoutDbCall() {
        when(ops.get(KEY)).thenReturn(shopJson(1L, "店A"));
        List<Shop> list = client.queryListWithPassThrough(KEY, Shop.class,
                () -> { throw new AssertionError("命中缓存不应查库"); },
                5L, TimeUnit.MINUTES);
        assertEquals(1, list.size());
        assertEquals("店A", list.get(0).getName());
        verify(ops, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void miss_callsDbAndBackfills() {
        when(ops.get(KEY)).thenReturn(null);
        List<Shop> list = client.queryListWithPassThrough(KEY, Shop.class,
                () -> List.of(shop(1L, "店A"), shop(2L, "店B")),
                5L, TimeUnit.MINUTES);
        assertEquals(2, list.size());
        // 回填缓存：JSON 序列化 + TTL
        verify(ops).set(eq(KEY), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void emptyList_cachesNullValue_toPreventPenetration() {
        when(ops.get(KEY)).thenReturn(null);
        List<Shop> list = client.queryListWithPassThrough(KEY, Shop.class,
                List::of, 5L, TimeUnit.MINUTES);
        assertNotNull(list);
        assertTrue(list.isEmpty());
        // 空值写入（CACHE_NULL_TTL=2 分钟），不写正常缓存
        verify(ops).set(eq(KEY), eq("[]"), anyLong(), eq(TimeUnit.MINUTES));
        verify(ops, never()).set(eq(KEY), anyString(), eq(5L), any());
    }

    @Test
    void nullValueCacheHit_returnsEmptyList_withoutDbCall() {
        when(ops.get(KEY)).thenReturn("");
        List<Shop> list = client.queryListWithPassThrough(KEY, Shop.class,
                () -> { throw new AssertionError("空值缓存命中不应查库"); },
                5L, TimeUnit.MINUTES);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void dbNull_returnsEmptyAndCachesNull() {
        when(ops.get(KEY)).thenReturn(null);
        List<Shop> list = client.queryListWithPassThrough(KEY, Shop.class,
                () -> null, 5L, TimeUnit.MINUTES);
        assertNotNull(list);
        assertTrue(list.isEmpty());
        verify(ops).set(eq(KEY), eq("[]"), anyLong(), eq(TimeUnit.MINUTES));
    }
}
