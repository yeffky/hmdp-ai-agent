package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserInfoMapper userInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    public UserServiceImpl(UserInfoMapper userInfoMapper, StringRedisTemplate stringRedisTemplate) {
        this.userInfoMapper = userInfoMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到redis
        Boolean allowed = stringRedisTemplate.opsForValue().setIfAbsent(
                LOGIN_CODE_COOLDOWN_KEY + phone, "1", LOGIN_CODE_RESEND_INTERVAL, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowed)) {
            return Result.fail("验证码发送过于频繁，请稍后再试");
        }
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5.发送验证码
        log.info("验证码已生成，phone={}, code={}", maskPhone(phone), code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2.从redis获取并校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        // 3.不一致报错
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 4.一致，根据手机号查询用户
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        User user = query().eq("phone", loginForm.getPhone()).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户保存
            user = createUserWithCode(phone);
        }

        // 7.生成 access + refresh 双 token
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String accessToken = jwtUtil.generateAccessToken(user.getId(), userDTO.getNickName(), userDTO.getIcon());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        // 8.将 User 转为 Hash 存 Redis（以 refreshToken 为 key，可吊销 + 存会话），TTL 对齐 refresh 有效期
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedname, filedValue) -> filedValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_REFRESH_KEY + refreshToken, userMap);
        stringRedisTemplate.expire(LOGIN_REFRESH_KEY + refreshToken, LOGIN_REFRESH_TTL_DAYS, TimeUnit.DAYS);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        return Result.ok(result);
    }

    @Override
    public Result refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Result.fail("refreshToken 不能为空");
        }
        // 1.校验 refreshToken 签名与有效期
        Long userId = jwtUtil.parseUserId(refreshToken);
        if (userId == null) {
            return Result.fail("登录已失效，请重新登录");
        }
        // 2.校验 Redis 会话（登出后此处查不到，实现吊销）
        String key = LOGIN_REFRESH_KEY + refreshToken;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            return Result.fail("登录已失效，请重新登录");
        }
        // 3.轮换 refreshToken：删除旧的，签发新的，续 TTL（滑动，空闲 7 天才过期）
        stringRedisTemplate.delete(key);
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        String newRefresh = jwtUtil.generateRefreshToken(userId);
        stringRedisTemplate.opsForHash().putAll(LOGIN_REFRESH_KEY + newRefresh, userMap);
        stringRedisTemplate.expire(LOGIN_REFRESH_KEY + newRefresh, LOGIN_REFRESH_TTL_DAYS, TimeUnit.DAYS);
        // 4.签发新 accessToken
        String newAccess = jwtUtil.generateAccessToken(userId, userDTO.getNickName(), userDTO.getIcon());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccess);
        result.put("refreshToken", newRefresh);
        return Result.ok(result);
    }

    @Override
    public Result sign() {
        // 获取用户
        Long userId = UserHolder.getUser().getId();

        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signToday() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        Boolean signed = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
        return Result.ok(Boolean.TRUE.equals(signed));
    }

    @Override
    public Result signCount() {
        // 获取用户
        Long userId = UserHolder.getUser().getId();

        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 获取本月截至今天为止的所有签到记录，返回的是十进制数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        log.info("当前日期对应bitmap:{}", result);
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        return Result.ok(countConsecutiveSigns(num));
    }

    /**
     * 统计连续签到天数：从今天（LSB）往回数连续 1，遇 0 停止。
     * 今天未签则右移一位从昨天起算（即统计过去几天连续签到的天数，不计今天）。
     *
     * 注意位序：BITFIELD GET u{day} 按 MSB-first（大端）解析，返回数值中
     * bit0(LSB) = 今天，位越高日期越早（bit(day-1) = 1号）。故"今天"位于最低位。
     *
     * @param bitmap BITFIELD GET unsigned(dayOfMonth) 返回的数值
     */
    int countConsecutiveSigns(long bitmap) {
        long bits = bitmap;
        // 今天未签：丢掉今天的位，从昨天开始往回数
        if ((bits & 1L) == 0) {
            bits >>= 1;
        }
        int count = 0;
        while ((bits & 1L) != 0) {
            count++;
            bits >>= 1;
        }
        return count;
    }

    private User createUserWithCode(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
