package com.hmdp.agent.memory.context;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * 启发式 Token 计数器。
 * 不需要外部 tokenizer，基于字符类型估算：
 * - 中文字符: ~1.5 chars/token
 * - 英文/其他字符: ~4 chars/token
 */
public final class TokenCounter {

    private TokenCounter() {}

    /**
     * 估算文本的 token 数
     */
    public static int count(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int otherChars = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);

            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }

        return (int) Math.ceil(chineseChars / 1.5) + (int) Math.ceil(otherChars / 4.0);
    }

    /**
     * 估算 LangChain4j ChatMessage 的 token 数
     */
    public static int count(ChatMessage message) {
        if (message == null) return 0;

        if (message instanceof UserMessage) {
            return count(((UserMessage) message).singleText());
        } else if (message instanceof AiMessage) {
            AiMessage ai = (AiMessage) message;
            return count(ai.text() != null ? ai.text() : "");
        } else if (message instanceof SystemMessage) {
            return count(((SystemMessage) message).text());
        }
        return 0;
    }

    /**
     * 估算消息列表的总 token 数
     */
    public static int count(Iterable<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage msg : messages) {
            total += count(msg);
        }
        return total;
    }

    /**
     * 估算 Map 格式消息列表（来自 state messages）的总 token 数
     */
    public static int countFromMap(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int total = 0;
        for (Map<String, String> msg : messages) {
            String content = msg.get("content");
            if (content != null) {
                total += count(content);
            }
        }
        return total;
    }
}
