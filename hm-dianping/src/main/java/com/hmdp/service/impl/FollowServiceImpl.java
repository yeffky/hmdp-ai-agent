package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result folllow(Long followUserId, Boolean isFollow) {
        // 1.判断是关注还是取关
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;

        if (isFollow) {
            // 2.关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户的id放入redis的set
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                // 维护计数：我关注数 +1，对方粉丝数 +1
                adjustCount(userId, "followee", 1);
                adjustCount(followUserId, "fans", 1);
            }

        } else {
            // 3.取关
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
                // 维护计数：我关注数 -1，对方粉丝数 -1
                adjustCount(userId, "followee", -1);
                adjustCount(followUserId, "fans", -1);
            }
        }
        return Result.ok();
    }

    /**
     * 维护 tb_user_info 的 fans/followee 计数。
     * 目标用户无记录时先插入默认行（其余字段走 DB 默认值 0），再递增/递减。
     * 递减用 GREATEST(.., 0) 防负（fans/followee 为 int UNSIGNED，不能为负）。
     * 注意：用字符串列名而非 Lambda，避免 MyBatis-Plus 3.4.3 在 JDK17 下解析
     * SerializedLambda 触发 InaccessibleObjectException。
     */
    private void adjustCount(Long targetUserId, String column, int delta) {
        if (userInfoService.getById(targetUserId) == null) {
            userInfoService.save(new UserInfo().setUserId(targetUserId));
        }
        String expr = delta >= 0
                ? column + " = " + column + " + " + delta
                : column + " = GREATEST(" + column + " - " + (-delta) + ", 0)";
        userInfoService.update().setSql(expr).eq("user_id", targetUserId).update();
    }

    @Override
    public Result followMy() {
        Long userId = UserHolder.getUser().getId();
        List<Long> ids = query().eq("user_id", userId).list().stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());
        if (ids.isEmpty()) return Result.ok(Collections.emptyList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result followFans() {
        Long userId = UserHolder.getUser().getId();
        List<Long> ids = query().eq("follow_user_id", userId).list().stream()
                .map(Follow::getUserId)
                .collect(Collectors.toList());
        if (ids.isEmpty()) return Result.ok(Collections.emptyList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result folllowCommons(Long id) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 2.求交集
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 3.判断id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
