package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /** 分页查询笔记评论（默认 5 条/页），补全用户信息，返回 {list,total,hasMore} */
    Result listComments(Long blogId, Integer current, Integer size);

    /** 发表笔记评论，并累加 tb_blog.comments 计数 */
    Result addComment(Long blogId, String content);
}
