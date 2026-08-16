package com.hmdp.agent.memory.reflection;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Reflexion 教训生成 —— 基于失败的运行上下文，用 LLM 生成一条结构化教训。
 * 参照 Reflexion (Shinn et al.)：失败后口头反思，供下次相似任务复用。
 */
@Component
public class ReflectionGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReflectionGenerator.class);

    @Resource
    private ChatModel model;

    /**
     * 生成教训。返回 {@link Reflection}（仅填充 domain/lesson/keywords），失败返回 null。
     *
     * @param userQuery    用户查询
     * @param plan         执行计划
     * @param results      收集到的数据片段（截断）
     * @param failureHint  失败信号描述
     */
    public Reflection generate(String sessionId, String userQuery, String plan,
                               String results, String failureHint) {
        String prompt = buildPrompt(userQuery, plan, results, failureHint);
        try {
            ChatResponse resp = model.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from("你是 Agent 反思助手。只输出 JSON，不要解释。"),
                            UserMessage.from(prompt))
                    .responseFormat(ResponseFormat.JSON)
                    .build());
            String raw = resp.aiMessage().text();
            log.info("Reflexion raw: {}", raw.length() > 200 ? raw.substring(0, 200) + "..." : raw);
            JSONObject jo = JSONUtil.parseObj(raw);
            Reflection r = new Reflection();
            r.setSessionId(sessionId);
            r.setDomain(jo.getStr("domain", "通用"));
            r.setLesson(jo.getStr("lesson", ""));
            r.setKeywords(jo.getStr("keywords", ""));
            if (r.getLesson() == null || r.getLesson().isBlank()) return null;
            return r;
        } catch (Exception e) {
            log.warn("Reflexion generation failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String userQuery, String plan, String results, String failureHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户问题\n").append(nullSafe(userQuery)).append("\n\n");
        sb.append("## 执行计划\n").append(nullSafe(plan)).append("\n\n");
        sb.append("## 收集到的数据（片段）\n").append(truncate(nullSafe(results), 800)).append("\n\n");
        sb.append("## 失败信号\n").append(nullSafe(failureHint)).append("\n\n");
        sb.append("""
                请写一条「反思教训」，供以后遇到相似查询时避免重蹈覆辙。要求：
                - 说清失败原因（如：按 food_category='火锅' AND score>=80 查为空、某工具参数缺失、SQL 列名写错）
                - 给出下次的正确做法（如：放宽评分条件、改用模糊匹配、换工具、先问用户补信息）
                - 只输出 JSON：{"domain":"领域标签（搜店/团购/订单/排队/通用等）","lesson":"一句话教训","keywords":"检索关键词（逗号分隔，2-6个，从用户问题提取）"}
                """);
        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "（无）" : s;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
