package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IShopCommentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 店铺评论
 */
@RestController
@RequestMapping("/shop-comment")
public class ShopCommentController {

    @Resource
    private IShopCommentService shopCommentService;

    /** 分页查询店铺评论（默认 5 条/页） */
    @GetMapping("/list/{shopId}")
    public Result list(@PathVariable Long shopId,
                       @RequestParam(value = "current", defaultValue = "1") Integer current,
                       @RequestParam(value = "size", defaultValue = "5") Integer size) {
        return shopCommentService.listComments(shopId, current, size);
    }

    /** 发表店铺评论：body {shopId, content, rating} */
    @PostMapping
    public Result add(@RequestBody Map<String, Object> body) {
        Long shopId = toLong(body.get("shopId"));
        String content = body.get("content") == null ? null : body.get("content").toString();
        Integer rating = body.get("rating") == null ? null : ((Number) body.get("rating")).intValue();
        return shopCommentService.addComment(shopId, content, rating);
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }
}
