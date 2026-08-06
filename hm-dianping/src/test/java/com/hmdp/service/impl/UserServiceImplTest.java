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

import static org.junit.jupiter.api.Assertions.*;
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
}
