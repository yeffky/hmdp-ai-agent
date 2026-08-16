package com.hmdp.utils;

/**
 * 用户输入清洗：去除控制字符（防 prompt 注入 / 终端逃逸）、截断超长输入。
 * 在用户消息进入 LLM prompt 前调用，作为 prompt 注入防护的第一道闸。
 */
public final class PromptSanitizer {

    private static final int DEFAULT_MAX_LEN = 2000;

    private PromptSanitizer() {
    }

    /** 清洗：去除控制字符（保留 \n \t \r）、截断到默认上限并 trim */
    public static String sanitize(String input) {
        return sanitize(input, DEFAULT_MAX_LEN);
    }

    public static String sanitize(String input, int maxLen) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\n' || c == '\t' || c == '\r') {
                sb.append(c);
                continue;
            }
            // C0 控制字符 + DEL 一律剔除
            if (c < 0x20 || c == 0x7F) {
                continue;
            }
            sb.append(c);
        }
        String cleaned = sb.toString().trim();
        if (cleaned.length() > maxLen) {
            cleaned = cleaned.substring(0, maxLen);
        }
        return cleaned;
    }
}
