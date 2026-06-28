package com.hmdp.agent.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 热记忆 —— 对话消息存入 Redis List，TTL 30min。
 * 作为 L1 缓存，快速读写，减少 MySQL 压力。
 */
public class RedisWorkingMemory implements ChatMemory {

    private final String sessionId;
    private final String key;
    private final StringRedisTemplate redis;
    private final int maxMessages;
    private final List<ChatMessage> messages = new ArrayList<>();

    private static final long TTL_SECONDS = 1800; // 30 min

    public RedisWorkingMemory(String sessionId, StringRedisTemplate redis, int maxMessages) {
        this.sessionId = sessionId;
        this.key = "chat:memory:" + sessionId;
        this.redis = redis;
        this.maxMessages = maxMessages;
        loadFromRedis();
    }

    @Override
    public Object id() { return sessionId; }

    private void loadFromRedis() {
        List<String> list = redis.opsForList().range(key, 0, maxMessages - 1);
        if (list == null) return;
        for (String json : list) {
            if (json.startsWith("U:")) {
                messages.add(UserMessage.from(json.substring(2)));
            } else if (json.startsWith("A:")) {
                messages.add(AiMessage.from(json.substring(2)));
            } else if (json.startsWith("S:")) {
                messages.add(SystemMessage.from(json.substring(2)));
            }
        }
    }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        while (messages.size() > maxMessages) messages.remove(0);

        // 写入 Redis
        String val;
        if (message instanceof UserMessage) {
            val = "U:" + ((UserMessage) message).singleText();
        } else if (message instanceof AiMessage) {
            val = "A:" + ((AiMessage) message).text();
        } else if (message instanceof SystemMessage) {
            val = "S:" + ((SystemMessage) message).text();
        } else {
            return;
        }
        redis.opsForList().rightPush(key, val);
        redis.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
        // 裁剪长度
        redis.opsForList().trim(key, -maxMessages, -1);
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
        redis.delete(key);
    }
}
