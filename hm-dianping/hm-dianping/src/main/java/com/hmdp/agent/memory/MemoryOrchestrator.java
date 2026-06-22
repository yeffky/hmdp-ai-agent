package com.hmdp.agent.memory;

import com.hmdp.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆编排器 — Redis L1 热缓存 + MySQL L2 持久化。
 * 读：Redis 命中则返回，miss 则从 MySQL 加载并回填 Redis
 * 写：同时写 Redis + 异步 MySQL
 */
public class MemoryOrchestrator implements ChatMemory {

    private final RedisWorkingMemory redisMemory;
    private final MySqlChatMemory mysqlMemory;

    public MemoryOrchestrator(String sessionId, StringRedisTemplate redis,
                               ChatMessageMapper mapper, int maxMessages) {
        this.redisMemory = new RedisWorkingMemory(sessionId, redis, maxMessages);
        this.mysqlMemory = new MySqlChatMemory(sessionId, mapper, maxMessages);
        // 如果 Redis 无数据，从 MySQL 加载并回填
        if (redisMemory.messages().isEmpty()) {
            List<ChatMessage> fromDb = mysqlMemory.messages();
            for (ChatMessage msg : fromDb) {
                redisMemory.add(msg);
            }
        }
    }

    @Override
    public Object id() {
        return redisMemory.id();
    }

    @Override
    public void add(ChatMessage message) {
        redisMemory.add(message);   // L1 实时写
        mysqlMemory.add(message);   // L2 持久化（同步，后续可改异步）
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> msgs = redisMemory.messages();
        if (!msgs.isEmpty()) return msgs;
        // Redis 过期 → 回源 MySQL
        msgs = mysqlMemory.messages();
        for (ChatMessage msg : msgs) {
            redisMemory.add(msg);
        }
        return msgs;
    }

    @Override
    public void clear() {
        redisMemory.clear();
        mysqlMemory.clear();
    }
}
