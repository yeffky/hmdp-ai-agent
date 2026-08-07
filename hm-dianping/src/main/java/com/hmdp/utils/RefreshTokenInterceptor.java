package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * accessToken 拦截器：无状态解析 JWT（验签 + 校验有效期），命中则写入 ThreadLocal。
 * accessToken 过期后由前端调用 /user/refresh 用 refreshToken 换发新 token（续约）。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public RefreshTokenInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        UserDTO user = jwtUtil.parseUser(token);
        if (user == null) {
            return true;
        }
        UserHolder.saveUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
