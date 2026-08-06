package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /** 分页查询笔记评论（默认 5 条/页） */
    @GetMapping("/list/{blogId}")
    public Result list(@PathVariable Long blogId,
                       @RequestParam(value = "current", defaultValue = "1") Integer current,
                       @RequestParam(value = "size", defaultValue = "5") Integer size) {
        return blogCommentsService.listComments(blogId, current, size);
    }

    /** 发表笔记评论：body {blogId, content} */
    @PostMapping
    public Result add(@RequestBody Map<String, Object> body) {
        Long blogId = body.get("blogId") == null ? null : Long.parseLong(body.get("blogId").toString());
        String content = body.get("content") == null ? null : body.get("content").toString();
        return blogCommentsService.addComment(blogId, content);
    }
}
