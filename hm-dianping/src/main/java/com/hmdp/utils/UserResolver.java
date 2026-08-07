package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

import javax.servlet.http.HttpServletRequest;

/**
 * 请求用户解析 — JWT 无状态鉴权下统一取当前用户。
 * 优先用 {@link RefreshTokenInterceptor} 已写入 UserHolder 的用户，兜底直接解析 JWT。
 * 旧 Redis 会话（login:token:）已无写入，不再回退。
 */
public final class UserResolver {

    private UserResolver() {}

    public static Long resolveUserId(HttpServletRequest request, JwtUtil jwtUtil) {
        UserDTO u = UserHolder.getUser();
        if (u != null) return u.getId();
        String token = request.getHeader("authorization");
        if (token == null || token.isBlank()) return null;
        return jwtUtil.parseUserId(token);
    }
}
