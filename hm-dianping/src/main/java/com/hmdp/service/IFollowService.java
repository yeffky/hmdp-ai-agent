package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result folllow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result folllowCommons(Long id);

    /** 我关注的人（当前登录用户） */
    Result followMy();

    /** 我的粉丝（关注了当前登录用户的人） */
    Result followFans();
}
