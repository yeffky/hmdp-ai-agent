package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopComment;
import com.hmdp.entity.User;
import com.hmdp.mapper.ShopCommentMapper;
import com.hmdp.service.IShopCommentService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShopCommentServiceImpl extends ServiceImpl<ShopCommentMapper, ShopComment> implements IShopCommentService {

    @Resource
    private IUserService userService;

    @Resource
    private IShopService shopService;

    @Override
    public Result listComments(Long shopId, Integer current, Integer size) {
        int s = (size == null || size < 1) ? 5 : size;
        Page<ShopComment> page = query()
                .eq("shop_id", shopId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .page(new Page<>(current == null ? 1 : current, s));
        List<ShopComment> list = page.getRecords();
        for (ShopComment c : list) {
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
    public Result addComment(Long shopId, String content, Integer rating) {
        Long userId = UserHolder.getUser().getId();
        if (shopId == null) return Result.fail("商铺ID不能为空");
        if (content == null || content.trim().isEmpty()) return Result.fail("评论内容不能为空");
        ShopComment c = new ShopComment();
        c.setShopId(shopId);
        c.setUserId(userId);
        c.setContent(content.trim());
        c.setRating(rating == null ? 5 : Math.min(5, Math.max(1, rating)));
        c.setLiked(0);
        c.setStatus(0);
        save(c);
        // 评分采用"延迟重算"：发评论只累加计数，均分由 ShopScoreRecalcScheduler 定时批量重算
        shopService.update().setSql("comments = comments + 1").eq("id", shopId).update();
        return Result.ok(c.getId());
    }
}
