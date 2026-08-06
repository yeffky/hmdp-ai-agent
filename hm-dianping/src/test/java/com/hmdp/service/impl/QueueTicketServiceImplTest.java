package com.hmdp.service.impl;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 排队取号核心逻辑单元测试 —— 覆盖"部分商家可关闭排队"守卫。
 */
class QueueTicketServiceImplTest {

    private static final Long TEST_SHOP_ID = 66666L;
    private static final Long TEST_USER_ID = 999999L;

    @Mock
    private IShopService shopService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private QueueTicketServiceImpl queueTicketService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        UserDTO user = new UserDTO();
        user.setId(TEST_USER_ID);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() throws Exception {
        UserHolder.removeUser();
        mocks.close();
    }

    private Shop shop(int queueEnabled) {
        return new Shop().setId(TEST_SHOP_ID).setQueueEnabled(queueEnabled);
    }

    @Test
    void takeNumber_shopNotSupportQueue_throwsAndNoRedis() {
        when(shopService.getById(TEST_SHOP_ID)).thenReturn(shop(0));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> queueTicketService.takeNumber(TEST_SHOP_ID, 2, null));
        assertEquals("该商铺暂不支持排队取号", e.getMessage());
        // 守卫在 Redis 操作之前，不应触发任何 Redis 访问
        verify(stringRedisTemplate, never()).opsForValue();
        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void takeNumber_shopNotExist_throws() {
        when(shopService.getById(TEST_SHOP_ID)).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> queueTicketService.takeNumber(TEST_SHOP_ID, 2, null));
        assertEquals("商铺不存在", e.getMessage());
    }

    @Test
    void takeNumber_shopSupportsQueue_proceeds() {
        when(shopService.getById(TEST_SHOP_ID)).thenReturn(shop(1));
        stubRedisChain();

        Map<String, Object> result = queueTicketService.takeNumber(TEST_SHOP_ID, 2, null);

        assertEquals(7L, result.get("queueNumber"));
        assertEquals(0L, result.get("aheadCount"));
        verify(stringRedisTemplate.opsForValue()).increment(anyString());
    }

    private void stubRedisChain() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(vo.get(anyString())).thenReturn(null);
        when(vo.increment(anyString())).thenReturn(7L);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);

        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zo = mock(ZSetOperations.class);
        when(zo.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(zo.rank(anyString(), anyString())).thenReturn(0L);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zo);

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> ho = mock(HashOperations.class);
        when(stringRedisTemplate.opsForHash()).thenReturn(ho);

        when(stringRedisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
    }
}
