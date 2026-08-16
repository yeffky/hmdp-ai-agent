package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopComment;

public interface IShopCommentService extends IService<ShopComment> {

    /** 分页查询店铺评论（默认每页 5 条），补全用户昵称/头像，返回 {list,total,hasMore} */
    Result listComments(Long shopId, Integer current, Integer size);

    /** 发表店铺评论，并累加 tb_shop.comments 计数 */
    Result addComment(Long shopId, String content, Integer rating);

    /** 按用户分页查询该用户发表的店铺评论（补全店铺名），返回 {list,total,hasMore} */
    Result listByUser(Long userId, Integer current, Integer size);
}
