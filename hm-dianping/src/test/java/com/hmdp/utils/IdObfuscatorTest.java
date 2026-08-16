package com.hmdp.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对外 ID 混淆器测试：
 * - 往返一致（encode → decode 还原真实 ID）
 * - decodeOrId 兼容纯数字真实 ID（普通前端 / 历史会话）
 * - 非法输入返回 null（防伪造）
 * - 字母表派生确定性（同盐同结果，不同盐不同字母表）
 */
class IdObfuscatorTest {

    private final IdObfuscator obf = new IdObfuscator("test-salt-4410760963");

    @Test
    void encodeDecode_roundTrip() {
        for (long id : new long[]{1L, 42L, 369L, 335L, 10012L, 1000000L, 3000000L, 2000000L, 123456789L}) {
            String code = obf.encode(id);
            assertNotNull(code);
            assertTrue(code.length() >= 6, "min length 6, got: " + code);
            assertEquals(id, obf.decode(code).longValue());
        }
    }

    @Test
    void encode_doesNotRevealRawId() {
        // 对外 ID 不得包含真实 ID 的数字（防直接暴露）
        String code = obf.encode(10012L);
        assertFalse(code.contains("10012"));
        // 不同 ID 编码不同
        assertNotEquals(obf.encode(1L), obf.encode(2L));
    }

    @Test
    void decodeOrId_pureDigitsTreatedAsRawId() {
        // 兼容：普通前端 / 历史会话传真实数字 ID
        assertEquals(10012L, obf.decodeOrId("10012").longValue());
        assertEquals(369L, obf.decodeOrId("369").longValue());
    }

    @Test
    void decodeOrId_roundTrip() {
        String code = obf.encode(369L);
        assertEquals(369L, obf.decodeOrId(code).longValue());
    }

    @Test
    void invalidInput_returnsNull() {
        assertNull(obf.decode(null));
        assertNull(obf.decode(""));
        assertNull(obf.decode("   "));
        assertNull(obf.decodeOrId(null));
        assertNull(obf.decodeOrId(""));
        // 伪造的 Sqids 串（往返校验失败）→ null，不误解析
        assertNull(obf.decode("zzzzzz"));
        assertNull(obf.decodeOrId("ab12cd!"));
    }

    @Test
    void nullSafe() {
        assertNull(obf.encode(null));
        assertNull(obf.encode(-5L));
    }

    @Test
    void alphabetDeterministicBySalt() {
        String a1 = IdObfuscator.deriveAlphabet("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", "salt-A");
        String a2 = IdObfuscator.deriveAlphabet("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", "salt-A");
        String a3 = IdObfuscator.deriveAlphabet("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", "salt-B");
        assertEquals(a1, a2, "same salt -> same alphabet");
        assertNotEquals(a1, a3, "different salt -> different alphabet");
        // 字母表是原字符集的排列（62 字符、无重复）
        assertEquals(62, a1.length());
        assertEquals(62, a1.chars().distinct().count());
    }

    @Test
    void differentSalt_differentEncoding() {
        IdObfuscator other = new IdObfuscator("another-salt");
        assertNotEquals(obf.encode(369L), other.encode(369L));
        // 各自可用自己的盐解码
        assertEquals(369L, other.decode(other.encode(369L)).longValue());
        // 交叉解码失败（不同字母表）：返回 null 或非原 id，均不可还原
        Long cross = other.decode(obf.encode(369L));
        assertTrue(cross == null || cross.longValue() != 369L);
    }
}
