package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Override
    public Result listComments(Long blogId, Integer current, Integer size) {
        int s = (size == null || size < 1) ? 5 : size;
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .page(new Page<>(current == null ? 1 : current, s));
        List<BlogComments> list = page.getRecords();
        for (BlogComments c : list) {
            User u = userService.getById(c.getUserId());
            if (u != null) {
                c.setNickName(u.getNickName());
                c.setIcon(u.getIcon());
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("list", list);
        res.put("total", page.getTotal());
        res.put("hasMore", page.getCurrent() * page.getSize() < page.getTotal());
        return Result.ok(res);
    }

    @Override
    public Result addComment(Long blogId, String content) {
        Long userId = UserHolder.getUser().getId();
        if (blogId == null) return Result.fail("笔记ID不能为空");
        if (content == null || content.trim().isEmpty()) return Result.fail("评论内容不能为空");
        BlogComments c = new BlogComments();
        c.setBlogId(blogId);
        c.setUserId(userId);
        c.setParentId(0L);
        c.setAnswerId(0L);
        c.setContent(content.trim());
        c.setLiked(0);
        c.setStatus(false);
        save(c);
        blogService.update().setSql("comments = comments + 1").eq("id", blogId).update();
        return Result.ok(c.getId());
    }
}
