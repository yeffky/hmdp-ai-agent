package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session, HttpServletResponse response){
        Result result = userService.login(loginForm, session);
        moveRefreshTokenToCookie(response, result);
        return result;
    }

    /**
     * 登出功能：删除 Redis 会话（吊销 refreshToken）并清掉 HttpOnly cookie
     */
    @PostMapping("/logout")
    public Result logout(HttpSession session, HttpServletResponse response,
                         @CookieValue(value = "refreshToken", required = false) String refreshToken){
        session.invalidate();
        UserHolder.removeUser();
        if (refreshToken != null && !refreshToken.isEmpty()) {
            stringRedisTemplate.delete(RedisConstants.LOGIN_REFRESH_KEY + refreshToken);
        }
        clearRefreshCookie(response);
        return Result.ok();
    }

    /** 用 refreshToken 换发新的 access/refresh 双 token（续约）；refreshToken 从 HttpOnly cookie 读取 */
    @PostMapping("/refresh")
    public Result refresh(HttpServletResponse response,
                          @CookieValue(value = "refreshToken", required = false) String refreshToken){
        Result result = userService.refresh(refreshToken);
        moveRefreshTokenToCookie(response, result);
        return result;
    }

    /**
     * 续约/登录成功时：把 refreshToken 写入 HttpOnly Cookie（JS 读不到，防 XSS 窃取长效凭证），
     * 并从响应体剥离，前端只需持有 accessToken。
     */
    @SuppressWarnings("unchecked")
    private void moveRefreshTokenToCookie(HttpServletResponse response, Result result) {
        if (result.getData() instanceof Map && ((Map<?, ?>) result.getData()).containsKey("refreshToken")) {
            Map<String, Object> data = (Map<String, Object>) result.getData();
            setRefreshCookie(response, data.get("refreshToken").toString());
            data.remove("refreshToken");
        }
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        // 直接拼 Set-Cookie 头（避开 Cookie.setSameSite 的 servlet-api 版本差异）
        String header = "refreshToken=" + refreshToken
                + "; HttpOnly; Path=/; SameSite=Lax"  // SameSite 缓解 CSRF；生产 HTTPS 应加 Secure
                + "; Max-Age=" + (RedisConstants.LOGIN_REFRESH_TTL_DAYS.intValue() * 24 * 3600); // 与 refresh TTL 对齐
        response.addHeader("Set-Cookie", header);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "refreshToken=; HttpOnly; Path=/; Max-Age=0");
    }

    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long userId){
        return Result.ok(BeanUtil.copyProperties(userService.getById(userId), UserDTO.class));
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

    /** 今天是否已签到 */
    @GetMapping("/sign/today")
    public Result signToday(){
        return userService.signToday();
    }

    /** 更新个人资料（仅本人）：city / introduce / gender / birthday */
    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserInfo userInfo){
        Long userId = UserHolder.getUser().getId();
        if (userInfo.getUserId() == null || !userId.equals(userInfo.getUserId())) {
            return Result.fail("只能编辑自己的资料");
        }
        UserInfo existing = userInfoService.getById(userId);
        if (existing == null) {
            // 首次填写资料：补默认值再插入
            userInfo.setFans(0);
            userInfo.setFollowee(0);
            userInfo.setCredits(0);
            userInfo.setLevel(false);
            userInfoService.save(userInfo);
        } else {
            userInfoService.updateById(userInfo);
        }
        return Result.ok();
    }
}
