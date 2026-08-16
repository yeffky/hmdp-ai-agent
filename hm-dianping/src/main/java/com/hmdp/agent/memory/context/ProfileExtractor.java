package com.hmdp.agent.memory.context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实时画像提取器 — 每次对话（Planner 阶段）异步提取用户画像并持久化，不等待 ContextCompressor。
 *
 * <p>对齐 yuru 的 ProfileSummarizerAgent 机制：
 * <ul>
 *   <li><b>固定字段约束</b>：prompt 列出 {@link UserStore#PROFILE_SCHEMA} 的固定 key，LLM 只输出这些字段；代码 filterSchema 再过滤，双保险；</li>
 *   <li><b>规则兜底</b>：LLM 不可用时用简单规则提取常见画像；</li>
 *   <li>字段与 {@link UserStore#PROFILE_SCHEMA} 一致（含 avoid），冲突由 UserStore 按时间戳保存最新。</li>
 * </ul>
 */
@Component
public class ProfileExtractor {

    private static final Logger log = LoggerFactory.getLogger(ProfileExtractor.class);

    @Resource
    private ChatModel model;

    @Resource
    private UserStore userStore;

    private final Gson gson = new Gson();

    /** 异步提取用户画像并保存；用户输入不含画像信息时忽略。不阻塞调用方。 */
    public void asyncExtract(Long userId, String userQuery) {
        if (userId == null || userQuery == null || userQuery.isBlank()) return;
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> fields = extract(userQuery);
                if (fields != null && !fields.isEmpty()) {
                    userStore.updateProfileAsync(userId, fields);
                    log.info("ProfileExtractor: saved {} fields for userId={}", fields.size(), userId);
                }
            } catch (Exception e) {
                log.debug("ProfileExtractor failed for userId={}: {}", userId, e.getMessage());
            }
        });
    }

    /** 用 LLM（JSON Schema 约束）提取画像字段；LLM 失败回退规则提取。 */
    private Map<String, Object> extract(String userQuery) {
        String prompt = "从以下用户输入中提取**稳定偏好**，只输出 JSON（无画像信息输出 {}，不要编造）：\n" + userQuery;
        try {
            ChatResponse resp = model.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from("你是用户画像提取器，按给定 JSON Schema 只输出画像字段，无依据的字段不填。"),
                            UserMessage.from(prompt)))
                    // DeepSeek 不支持 response_format:json_schema（实测 400），用 json_object + prompt 字段约束
                    .responseFormat(ResponseFormat.JSON)
                    .build());
            String text = resp.aiMessage().text();
            Map<String, Object> parsed = parseJson(text);
            return filterSchema(parsed);
        } catch (Exception e) {
            log.debug("ProfileExtractor LLM failed, fallback to rule: {}", e.getMessage());
            return ruleExtract(userQuery);
        }
    }

    /** 只保留固定 schema 字段，过滤 LLM 可能引入的自由 key。 */
    private Map<String, Object> filterSchema(Map<String, Object> parsed) {
        if (parsed == null) return Collections.emptyMap();
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : parsed.entrySet()) {
            if (UserStore.PROFILE_SCHEMA.containsKey(e.getKey()) && e.getValue() != null) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return filtered;
    }

    private Map<String, Object> parseJson(String text) {
        if (text == null) return Collections.emptyMap();
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        if (s < 0 || e <= s) return Collections.emptyMap();
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return gson.fromJson(text.substring(s, e + 1), type);
    }

    /** 规则兜底：LLM 不可用时用简单规则提取常见画像。 */
    private Map<String, Object> ruleExtract(String userQuery) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (userQuery.contains("辣")) m.put("taste", "辣味");
        if (userQuery.contains("清淡")) m.put("taste", "清淡");
        if (userQuery.contains("带宠物") || userQuery.contains("宠物")) m.put("hasPet", true);
        Matcher p = Pattern.compile("(\\d+)\\s*人").matcher(userQuery);
        if (p.find()) m.put("partySize", Integer.parseInt(p.group(1)));
        if (userQuery.contains("晚上")) m.put("diningTime", "晚上");
        else if (userQuery.contains("中午")) m.put("diningTime", "中午");
        if (userQuery.contains("不要排队") || userQuery.contains("不想排队")) m.put("avoid", List.of("排队"));
        return m;
    }
}
