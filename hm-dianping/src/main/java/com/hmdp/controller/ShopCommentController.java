package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IShopCommentService;
import com.hmdp.utils.IdObfuscator;
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

    @Resource
    private IdObfuscator idObfuscator;

    /** 分页查询店铺评论（默认 5 条/页）；shopId 支持对外混淆 ID 或数据库真实 ID */
    @GetMapping("/list/{shopId}")
    public Result list(@PathVariable String shopId,
                       @RequestParam(value = "current", defaultValue = "1") Integer current,
                       @RequestParam(value = "size", defaultValue = "5") Integer size) {
        Long realShopId = idObfuscator.decodeOrId(shopId);
        if (realShopId == null) {
            return Result.fail("店铺不存在");
        }
        return shopCommentService.listComments(realShopId, current, size);
    }

    /** 发表店铺评论：body {shopId, content, rating}；shopId 支持对外混淆 ID 或数据库真实 ID */
    @PostMapping
    public Result add(@RequestBody Map<String, Object> body) {
        Long shopId = idObfuscator.decodeOrId(body.get("shopId") == null ? null : String.valueOf(body.get("shopId")));
        String content = body.get("content") == null ? null : body.get("content").toString();
        Integer rating = body.get("rating") == null ? null : ((Number) body.get("rating")).intValue();
        return shopCommentService.addComment(shopId, content, rating);
    }

    /** 按用户分页查询其发表的店铺评论（个人主页评价 tab 用） */
    @GetMapping("/of/user/{userId}")
    public Result ofUser(@PathVariable Long userId,
                         @RequestParam(value = "current", defaultValue = "1") Integer current,
                         @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return shopCommentService.listByUser(userId, current, size);
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }
}
