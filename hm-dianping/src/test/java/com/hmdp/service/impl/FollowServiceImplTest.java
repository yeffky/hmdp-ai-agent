package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 关注/取关：维护 tb_user_info 的 fans/followee 计数 + 粉丝/关注列表。
 */
class FollowServiceImplTest {

    private static final Long ME = 1L;
    private static final Long TARGET = 2L;

    @Mock private IUserService userService;
    @Mock private IUserInfoService userInfoService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private FollowMapper followMapper;

    @Spy
    @InjectMocks
    private FollowServiceImpl followService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(followService, "baseMapper", followMapper);
        UserDTO me = new UserDTO();
        me.setId(ME);
        UserHolder.saveUser(me);
    }

    @AfterEach
    void tearDown() throws Exception {
        UserHolder.removeUser();
        mocks.close();
    }

    @SuppressWarnings("unchecked")
    private UpdateChainWrapper<UserInfo> stubUserInfoUpdate() {
        UpdateChainWrapper<UserInfo> chain = mock(UpdateChainWrapper.class);
        when(chain.setSql(anyString())).thenReturn(chain);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(true);
        when(userInfoService.update()).thenReturn(chain);
        return chain;
    }

    @SuppressWarnings("unchecked")
    private void stubSetOps() {
        SetOperations<String, String> so = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(so);
        when(so.add(anyString(), any())).thenReturn(1L);
        when(so.remove(anyString(), any())).thenReturn(1L);
    }

    // ---------- 关注/取关维护计数 ----------

    @Test
    void follow_success_maintainsFolloweeAndFans() {
        doReturn(true).when(followService).save(any());
        stubSetOps();
        UpdateChainWrapper<UserInfo> chain = stubUserInfoUpdate();
        // 双方都已有 UserInfo 记录
        when(userInfoService.getById(ME)).thenReturn(new UserInfo().setUserId(ME));
        when(userInfoService.getById(TARGET)).thenReturn(new UserInfo().setUserId(TARGET));

        Result r = followService.folllow(TARGET, true);
        assertTrue(r.getSuccess());

        verify(chain).setSql("followee = followee + 1");
        verify(chain).setSql("fans = fans + 1");
        verify(userInfoService, never()).save(any());
    }

    @Test
    void follow_createsUserInfoWhenTargetMissing() {
        doReturn(true).when(followService).save(any());
        stubSetOps();
        stubUserInfoUpdate();
        when(userInfoService.getById(ME)).thenReturn(new UserInfo().setUserId(ME));
        when(userInfoService.getById(TARGET)).thenReturn(null); // 被关注用户无资料记录

        followService.folllow(TARGET, true);

        verify(userInfoService).save(argThat(ui -> TARGET.equals(((UserInfo) ui).getUserId())));
    }

    @Test
    void unfollow_success_decrementsCountsWithFloorZero() {
        doReturn(true).when(followService).remove(any());
        stubSetOps();
        UpdateChainWrapper<UserInfo> chain = stubUserInfoUpdate();
        when(userInfoService.getById(ME)).thenReturn(new UserInfo().setUserId(ME));
        when(userInfoService.getById(TARGET)).thenReturn(new UserInfo().setUserId(TARGET));

        Result r = followService.folllow(TARGET, false);
        assertTrue(r.getSuccess());

        verify(chain).setSql("followee = GREATEST(followee - 1, 0)");
        verify(chain).setSql("fans = GREATEST(fans - 1, 0)");
    }

    // ---------- 粉丝/关注列表 ----------

    @SuppressWarnings("unchecked")
    private QueryChainWrapper<Follow> stubQuery(List<Follow> records) {
        QueryChainWrapper<Follow> chain = mock(QueryChainWrapper.class);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.list()).thenReturn(records);
        doReturn(chain).when(followService).query();
        return chain;
    }

    private User user(long id, String nick) {
        return new User().setId(id).setNickName(nick).setIcon("/" + id + ".png");
    }

    @Test
    void followMy_returnsFollowingUsers() {
        stubQuery(List.of(
                new Follow().setUserId(ME).setFollowUserId(2L),
                new Follow().setUserId(ME).setFollowUserId(3L)));
        when(userService.listByIds(anyList())).thenReturn(List.of(user(2L, "小明"), user(3L, "小红")));

        Result r = followService.followMy();
        assertTrue(r.getSuccess());
        List<UserDTO> users = (List<UserDTO>) r.getData();
        assertEquals(2, users.size());
        assertEquals(2L, users.get(0).getId());
        assertEquals("小明", users.get(0).getNickName());
    }

    @Test
    void followMy_noFollowing_returnsEmpty() {
        stubQuery(Collections.emptyList());
        Result r = followService.followMy();
        assertTrue(r.getSuccess());
        assertTrue(((List<?>) r.getData()).isEmpty());
    }

    @Test
    void followFans_returnsFollowers() {
        stubQuery(List.of(
                new Follow().setUserId(5L).setFollowUserId(ME),
                new Follow().setUserId(6L).setFollowUserId(ME)));
        when(userService.listByIds(anyList())).thenReturn(List.of(user(5L, "阿五"), user(6L, "阿六")));

        Result r = followService.followFans();
        assertTrue(r.getSuccess());
        List<UserDTO> users = (List<UserDTO>) r.getData();
        assertEquals(2, users.size());
        assertEquals(5L, users.get(0).getId());
    }
}
