package com.hmdp.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 响应缓存包装 —— 对**无工具调用且非结构化输出**的请求按 (model + messages + 参数) 哈希缓存，
 * 命中直接返回缓存响应，减少重复 LLM 调用（省成本/延迟，也缓解连接抖动）。
 *
 * <p>不缓存：带工具调用的请求（依赖会话状态）；带 responseFormat 的请求（Planner 等结构化输出，
 * 缓存会返回过期规划）。流式模型单独走 streaming，不经此包装。
 */
public class CachingChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(CachingChatModel.class);

    private final ChatModel delegate;
    private final long ttlMs;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingChatModel(ChatModel delegate, long ttlMs) {
        this.delegate = delegate;
        this.ttlMs = ttlMs;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        // 不缓存两类请求：
        // 1) 带工具调用的请求（依赖会话/执行链状态，缓存危险）；
        // 2) 结构化输出请求（responseFormat=json，如 Planner 的意图/计划）——意图随上下文与轮次变化，
        //    缓存会返回过期规划（如"帮我取号"命中 30 分钟前的旧 plan）。
        // 流式模型单独走 streaming，不经此包装。
        if ((request.toolSpecifications() != null && !request.toolSpecifications().isEmpty())
                || request.responseFormat() != null) {
            return delegate.chat(request);
        }
        String key = buildKey(request);
        long now = System.currentTimeMillis();
        Entry hit = cache.get(key);
        if (hit != null && now < hit.expireAt) {
            return hit.response;
        }
        ChatResponse resp = delegate.chat(request);
        cache.put(key, new Entry(resp, now + ttlMs));
        if (cache.size() > 2000) evictExpired(now);
        return resp;
    }

    /** 缓存 key：model + 消息序列 + 采样参数 + 输出格式。 */
    private String buildKey(ChatRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.modelName() != null) sb.append(req.modelName()).append('|');
        List<ChatMessage> msgs = req.messages();
        if (msgs != null) {
            for (ChatMessage m : msgs) {
                sb.append(m.type()).append(':').append(text(m)).append(';');
            }
        }
        if (req.temperature() != null) sb.append("|t").append(req.temperature());
        if (req.maxOutputTokens() != null) sb.append("|m").append(req.maxOutputTokens());
        if (req.responseFormat() != null) sb.append("|rf").append(req.responseFormat().type());
        return sha256(sb.toString());
    }

    private static String text(ChatMessage m) {
        if (m instanceof AiMessage a) {
            return a.toolExecutionRequests() != null && !a.toolExecutionRequests().isEmpty() ? "(工具)" : a.text();
        }
        if (m instanceof UserMessage u) return u.singleText();
        if (m instanceof SystemMessage s) return s.text();
        if (m instanceof ToolExecutionResultMessage t) return t.text();
        return "";
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private void evictExpired(long now) {
        cache.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }

    private static class Entry {
        final ChatResponse response;
        final long expireAt;

        Entry(ChatResponse r, long e) {
            this.response = r;
            this.expireAt = e;
        }
    }
}
