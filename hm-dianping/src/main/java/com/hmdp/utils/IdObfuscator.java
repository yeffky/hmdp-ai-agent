package com.hmdp.utils;

import org.sqids.Sqids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Random;

/**
 * 对外 ID 混淆器（Sqids）：数据库自增 ID 不以明文暴露给前端 / LLM（主流做法参考 Stripe 的 opaque ID 思路）。
 *
 * <p>策略：用服务端盐（{@code hmdp.id-obfuscator.salt}）对标准字母表做确定性洗牌（SHA-256 种子 + Fisher-Yates），
 * 再以洗牌后的字母表构造 Sqids。外部不知道盐就推导不出字母表，也就无法把混淆 ID 反推回真实 ID；
 * 同时 Sqids 有最短长度（minLength=6）与内置脏词过滤，输出对 URL 安全且短小。
 *
 * <p>边界约定：
 * <ul>
 *   <li>工具结果 / 卡片 / {@code [[...]]} 占位符里的店铺、团购 ID 一律用 {@link #encode(Long)} 输出；</li>
 *   <li>LLM 回传的工具参数（shopId 等）用 {@link #decodeOrId(String)} 还原——纯数字视为真实 ID（兼容历史会话/兜底链路），
 *       非纯数字按 Sqids 解码；</li>
 *   <li>REST 入口（如 {@code /shop/{id}}）用 {@link #decodeOrId(String)}，普通前端传真实 ID 不受影响。</li>
 * </ul>
 */
@Component
public class IdObfuscator {

    /** Sqids 默认字母表（62 字符，无重复）。 */
    private static final String DEFAULT_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final String salt;
    private volatile Sqids sqids;

    public IdObfuscator(@Value("${hmdp.id-obfuscator.salt:hmdp-agent-dev-salt}") String salt) {
        this.salt = salt == null || salt.isBlank() ? "hmdp-agent-dev-salt" : salt;
    }

    @PostConstruct
    void init() {
        this.sqids = Sqids.builder()
                .alphabet(deriveAlphabet(DEFAULT_ALPHABET, salt))
                .minLength(6)
                .build();
    }

    /** 内部 ID → 对外混淆 ID；null 安全。 */
    public String encode(Long id) {
        if (id == null || id < 0) return null;
        Sqids s = sqids();
        return s.encode(List.of(id));
    }

    /**
     * 对外 ID → 内部 ID（严格）：按 Sqids 解码并做往返校验
     * （decode 结果再 encode 必须与原串一致，防止把任意形似 Sqids 的串误解析成错误 ID），非法输入返回 null。
     */
    public Long decode(String code) {
        if (code == null || code.isBlank()) return null;
        String t = code.trim();
        try {
            List<Long> list = sqids().decode(t);
            if (list.isEmpty()) return null;
            long id = list.get(0);
            return t.equals(encode(id)) ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对外 ID → 内部 ID（宽松，推荐入口）：纯数字视为真实内部 ID 直接解析（兼容普通前端 / 历史会话 /
     * 兜底链路传真实 ID 的场景）；非纯数字按 {@link #decode(String)} 解码（含往返校验）。
     */
    public Long decodeOrId(String code) {
        if (code == null || code.isBlank()) return null;
        String t = code.trim();
        if (t.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return decode(t);
    }

    private Sqids sqids() {
        Sqids s = sqids;
        if (s == null) {
            synchronized (this) {
                if (sqids == null) init();
                s = sqids;
            }
        }
        return s;
    }

    /** 用盐做确定性洗牌：SHA-256(盐) 前 8 字节作为种子 → Fisher-Yates。同一盐结果稳定。 */
    static String deriveAlphabet(String alphabet, String salt) {
        char[] chars = alphabet.toCharArray();
        Random rnd = new Random(seedFromSalt(salt));
        for (int i = chars.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            char t = chars[i];
            chars[i] = chars[j];
            chars[j] = t;
        }
        return new String(chars);
    }

    private static long seedFromSalt(String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(salt.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (h[i] & 0xFFL);
            }
            return seed;
        } catch (Exception e) {
            return salt.hashCode();
        }
    }
}
