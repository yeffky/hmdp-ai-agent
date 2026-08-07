package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 双 token（access + refresh）JWT 工具。
 * - accessToken：短效（默认 30min），携带用户身份，接口无状态验签，过期由客户端用 refresh 换取新 token
 * - refreshToken：长效（默认 7 天），存 Redis 可吊销，仅用于换发新的 access/refresh，到期才需重新登录
 */
@Component
public class JwtUtil {

    @Value("${hmdp.jwt.secret}")
    private String secret;

    @Value("${hmdp.jwt.access-ttl-minutes:30}")
    private long accessTtlMinutes;

    @Value("${hmdp.jwt.refresh-ttl-days:7}")
    private long refreshTtlDays;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** accessToken：携带 userId + 昵称 + 头像 */
    public String generateAccessToken(Long userId, String nickName, String icon) {
        return build(userId, accessTtlMinutes * 60_000L)
                .claim("nickName", nickName == null ? "" : nickName)
                .claim("icon", icon == null ? "" : icon)
                .compact();
    }

    /** refreshToken：仅携带 userId，长有效期 */
    public String generateRefreshToken(Long userId) {
        return build(userId, refreshTtlDays * 24 * 3600_000L).compact();
    }

    private JwtBuilder build(Long userId, long ttlMillis) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key(), SignatureAlgorithm.HS256);
    }

    /** 解析并校验 JWT，无效/过期返回 null */
    public Claims parse(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析出 userId；无效返回 null */
    public Long parseUserId(String token) {
        Claims c = parse(token);
        return c == null ? null : Long.valueOf(c.getSubject());
    }

    /** 解析 accessToken 为用户（含昵称/头像）；无效返回 null */
    public UserDTO parseUser(String token) {
        Claims c = parse(token);
        if (c == null) return null;
        UserDTO u = new UserDTO();
        u.setId(Long.valueOf(c.getSubject()));
        Object n = c.get("nickName");
        u.setNickName(n == null ? "" : n.toString());
        Object i = c.get("icon");
        u.setIcon(i == null ? "" : i.toString());
        return u;
    }
}
