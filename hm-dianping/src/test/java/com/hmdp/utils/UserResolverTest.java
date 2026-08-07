package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UserResolver — JWT 无状态鉴权下解析当前用户（UserHolder 优先，JWT 兜底）。
 */
class UserResolverTest {

    private static JwtUtil jwtUtil() {
        JwtUtil u = new JwtUtil();
        ReflectionTestUtils.setField(u, "secret", "user-resolver-test-secret-0123456789-abcdef");
        ReflectionTestUtils.setField(u, "accessTtlMinutes", 30L);
        ReflectionTestUtils.setField(u, "refreshTtlDays", 7L);
        return u;
    }

    @Test
    void usesUserHolderFirst() {
        UserDTO dto = new UserDTO();
        dto.setId(123L);
        UserHolder.saveUser(dto);
        try {
            // jwtUtil 传 null 也能取到（UserHolder 优先，不触达 JWT）
            Long id = UserResolver.resolveUserId(new MockHttpServletRequest(), null);
            assertEquals(123L, id);
        } finally {
            UserHolder.removeUser();
        }
    }

    @Test
    void fallsBackToJwtParse() {
        JwtUtil jwt = jwtUtil();
        String token = jwt.generateAccessToken(456L, "张三", "");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("authorization", token);
        Long id = UserResolver.resolveUserId(req, jwt);
        assertEquals(456L, id);
    }

    @Test
    void returnsNullWithoutUserOrToken() {
        Long id = UserResolver.resolveUserId(new MockHttpServletRequest(), jwtUtil());
        assertNull(id);
    }
}
