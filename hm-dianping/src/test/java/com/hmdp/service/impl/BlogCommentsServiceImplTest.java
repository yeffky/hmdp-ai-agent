package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogService;
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
 * 笔记评论：分页（补全用户信息/总数）+ 发表（累加 tb_blog.comments）。
 */
class BlogCommentsServiceImplTest {

    @Mock private BlogCommentsMapper blogCommentsMapper;
    @Mock private IUserService userService;
    @Mock private IBlogService blogService;

    @Spy
    @InjectMocks
    private BlogCommentsServiceImpl blogCommentsService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(blogCommentsService, "baseMapper", blogCommentsMapper);
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
        Page<BlogComments> page = new Page<>(1, 5);
        page.setRecords(List.of(
                new BlogComments().setId(1L).setBlogId(5L).setUserId(2L).setContent("种草了"),
                new BlogComments().setId(2L).setBlogId(5L).setUserId(3L).setContent("收藏")));
        page.setTotal(12);
        when(blogCommentsMapper.selectPage(any(Page.class), any())).thenReturn(page);
        User u1 = new User();
        u1.setNickName("觅食小分队");
        when(userService.getById(2L)).thenReturn(u1);
        when(userService.getById(3L)).thenReturn(null);

        Result r = blogCommentsService.listComments(5L, 1, 5);
        assertTrue(r.getSuccess());
        Map<String, Object> data = (Map<String, Object>) r.getData();
        List<BlogComments> list = (List<BlogComments>) data.get("list");
        assertEquals(2, list.size());
        assertEquals(12L, ((Number) data.get("total")).longValue());
        assertTrue((Boolean) data.get("hasMore"));
        assertEquals("觅食小分队", list.get(0).getNickName());
    }

    @Test
    void addComment_emptyContent_fails() {
        assertFalse(blogCommentsService.addComment(5L, "  ").getSuccess());
        verify(blogCommentsMapper, never()).insert(any());
    }

    @Test
    void addComment_success_savesAndIncrementsCount() {
        @SuppressWarnings("unchecked")
        UpdateChainWrapper<Blog> chain = mock(UpdateChainWrapper.class);
        when(chain.setSql(anyString())).thenReturn(chain);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(true);
        when(blogService.update()).thenReturn(chain);

        Result r = blogCommentsService.addComment(5L, "写得太好了");
        assertTrue(r.getSuccess());
        ArgumentCaptor<BlogComments> cap = ArgumentCaptor.forClass(BlogComments.class);
        verify(blogCommentsMapper).insert(cap.capture());
        assertEquals(5L, cap.getValue().getBlogId());
        assertEquals("写得太好了", cap.getValue().getContent());
        verify(chain).setSql("comments = comments + 1");
    }
}
