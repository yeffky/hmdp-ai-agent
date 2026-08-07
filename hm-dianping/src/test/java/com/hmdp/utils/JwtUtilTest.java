package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 双 token（access + refresh）JWT 工具：签发/解析往返、篡改/换钥/空值兜底。
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-at-least-32-chars-long-123456");
        ReflectionTestUtils.setField(jwtUtil, "accessTtlMinutes", 30L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTtlDays", 7L);
    }

    @Test
    void accessToken_generateThenParse_roundTrip() {
        String token = jwtUtil.generateAccessToken(123456789L, "小鱼同学", "/a.png");
        assertNotNull(token);
        UserDTO user = jwtUtil.parseUser(token);
        assertNotNull(user);
        assertEquals(123456789L, user.getId());
        assertEquals("小鱼同学", user.getNickName());
        assertEquals("/a.png", user.getIcon());
    }

    @Test
    void refreshToken_generateThenParseUserId() {
        String token = jwtUtil.generateRefreshToken(123456789L);
        assertEquals(123456789L, jwtUtil.parseUserId(token));
    }

    @Test
    void parse_invalidToken_returnsNull() {
        assertNull(jwtUtil.parseUser("invalid.token.here"));
        assertNull(jwtUtil.parseUserId(""));
        assertNull(jwtUtil.parseUserId(null));
    }

    @Test
    void parse_tokenSignedWithWrongKey_returnsNull() {
        String token = Jwts.builder()
                .setSubject("999")
                .signWith(Keys.hmacShaKeyFor("another-key-32-characters-long!!".getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
        assertNull(jwtUtil.parseUserId(token), "换钥后应校验失败");
    }
}
