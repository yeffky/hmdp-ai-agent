package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopComment;
import com.hmdp.entity.User;
import com.hmdp.mapper.ShopCommentMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 店铺评论：分页（补全用户信息/总数）+ 发表（累加计数）。
 */
class ShopCommentServiceImplTest {

    @Mock private ShopCommentMapper shopCommentMapper;
    @Mock private IUserService userService;
    @Mock private IShopService shopService;

    @Spy
    @InjectMocks
    private ShopCommentServiceImpl shopCommentService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(shopCommentService, "baseMapper", shopCommentMapper);
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
    void listComments_returnsPageWithUsersAndTotal() {
        Page<ShopComment> page = new Page<>(1, 5);
        page.setRecords(List.of(
                new ShopComment().setId(1L).setShopId(9L).setUserId(2L).setContent("好吃"),
                new ShopComment().setId(2L).setShopId(9L).setUserId(3L).setContent("不错")));
        page.setTotal(7);
        when(shopCommentMapper.selectPage(any(Page.class), any())).thenReturn(page);
        User u1 = new User();
        u1.setNickName("达人A");
        u1.setIcon("/a.png");
        User u2 = new User();
        u2.setNickName("达人B");
        when(userService.getById(2L)).thenReturn(u1);
        when(userService.getById(3L)).thenReturn(u2);

        Result r = shopCommentService.listComments(9L, 1, 5);
        assertTrue(r.getSuccess());
        Map<String, Object> data = (Map<String, Object>) r.getData();
        List<ShopComment> list = (List<ShopComment>) data.get("list");
        assertEquals(2, list.size());
        assertEquals(7L, ((Number) data.get("total")).longValue());
        assertTrue((Boolean) data.get("hasMore"));
        assertEquals("达人A", list.get(0).getNickName());
        assertEquals("/a.png", list.get(0).getIcon());
    }

    @Test
    void addComment_emptyContent_fails() {
        assertFalse(shopCommentService.addComment(9L, "   ", 5).getSuccess());
        verify(shopCommentMapper, never()).insert(any());
    }

    @Test
    void addComment_success_savesAndIncrementsCount() {
        @SuppressWarnings("unchecked")
        UpdateChainWrapper<Shop> chain = mock(UpdateChainWrapper.class);
        when(chain.setSql(anyString())).thenReturn(chain);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(true);
        when(shopService.update()).thenReturn(chain);

        Result r = shopCommentService.addComment(9L, "很棒的店", 5);
        assertTrue(r.getSuccess());
        ArgumentCaptor<ShopComment> cap = ArgumentCaptor.forClass(ShopComment.class);
        verify(shopCommentMapper).insert(cap.capture());
        assertEquals(9L, cap.getValue().getShopId());
        assertEquals("很棒的店", cap.getValue().getContent());
        assertEquals(1L, cap.getValue().getUserId());
        verify(chain).setSql("comments = comments + 1");
    }
}
