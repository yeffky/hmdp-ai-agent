package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 每日签到状态：今天是否已签到（置灰按钮用）。
 */
class UserServiceImplTest {

    @Mock private UserInfoMapper userInfoMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        UserDTO u = new UserDTO();
        u.setId(1L);
        UserHolder.saveUser(u);
    }

    @AfterEach
    void tearDown() throws Exception {
        UserHolder.removeUser();
        mocks.close();
    }

    @Test
    void signToday_returnsTrueWhenBitSet() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);
        when(vo.getBit(anyString(), anyLong())).thenReturn(true);

        Result r = userService.signToday();
        assertTrue(r.getSuccess());
        assertTrue((Boolean) r.getData());
    }

    @Test
    void signToday_returnsFalseWhenBitNotSet() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);
        when(vo.getBit(anyString(), anyLong())).thenReturn(false);

        Result r = userService.signToday();
        assertTrue(r.getSuccess());
        assertFalse((Boolean) r.getData());
    }

    // ---- 连续签到统计（BITFIELD 为 MSB-first 位序：bit0=今天，越高位越早） ----

    @Test
    void countConsecutiveSigns_countsYesterdayContinuous_whenTodayNotSigned() {
        // 核心回归：真实 Redis 数据 bitmap=14（0b0001110）= 4、5、6 号签、今天 7 号未签
        // bit1/2/3=1（对应 6/5/4 号），bit0=0（今天 7 号）→ 右移后从昨天起算，连续 3 天
        assertEquals(3, userService.countConsecutiveSigns(14L));
    }

    @Test
    void countConsecutiveSigns_countsAllSigned_whenTodaySigned() {
        // 1~7 号全签（bit0..6=1，今天 7 号在 bit0）→ 7 天
        assertEquals(7, userService.countConsecutiveSigns(0b1111111L));
    }

    @Test
    void countConsecutiveSigns_returnsZero_whenRecentDaysNotSigned() {
        // 仅 3 号签（bit4=1），5、6、7 号均未签 → 最近无连续
        assertEquals(0, userService.countConsecutiveSigns(16L));
    }

    @Test
    void countConsecutiveSigns_stopsAtFirstBreak() {
        // 今天(bit0)签、昨天(bit1)未签、前天(bit2)签 → 连续仅 1 天
        assertEquals(1, userService.countConsecutiveSigns(0b101L));
    }

    @Test
    void countConsecutiveSigns_handlesFirstDayAndWholeMonth() {
        assertEquals(1, userService.countConsecutiveSigns(1L));              // 今天 1 号已签
        assertEquals(0, userService.countConsecutiveSigns(0L));              // 今天未签且无历史
        assertEquals(31, userService.countConsecutiveSigns((1L << 31) - 1)); // 全月 31 天
    }

    @Test
    void signCount_returnsZero_whenNoBitsSet() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);
        when(vo.bitField(anyString(), any())).thenReturn(Collections.singletonList(0L));

        Result r = userService.signCount();
        assertTrue(r.getSuccess());
        assertEquals(0, r.getData());
    }
}
