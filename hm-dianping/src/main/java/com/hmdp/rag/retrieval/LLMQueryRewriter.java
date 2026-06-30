package com.hmdp.rag.retrieval;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * LLM 驱动的通用查询改写器 — 理解用户口语化查询，生成多个向量检索变体。
 *
 * <p>不绑定特定业务。LLM 自行判断查询意图（商家检索/平台政策/订单/优惠券等），
 * 生成 2~3 个改写查询变体，每个变体独立走向量检索，最后 RRF 融合去重排序。
 */
public class LLMQueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(LLMQueryRewriter.class);

    private final OpenAiChatModel model;

    private static final String SYSTEM_PROMPT = """
            你是RAG检索的查询改写器。用户输入口语化查询，你需要理解意图并生成2~3个改写变体，
            用于提升向量检索召回率。改写时应补充相关上下文词、同义词、正式表述等。

            只输出 JSON 数组，不要其他内容：
            ["改写变体1", "改写变体2", "改写变体3"]""";

    public LLMQueryRewriter(OpenAiChatModel model) {
        this.model = model;
    }

    /**
     * 改写用户查询，返回多个向量检索变体（含原始查询）。
     * LLM 调用失败时降级为仅原始查询。
     */
    public List<String> rewrite(String query) {
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from("用户查询: " + query)));
            String raw = resp.aiMessage().text().trim();
            log.info("LLM query rewrite: '{}' -> {}", query, raw);

            List<String> variants = extractJsonArray(raw);
            if (variants.isEmpty()) {
                return List.of(query);
            }
            // 确保原始查询在列表中（去重 + 前置）
            Set<String> seen = new LinkedHashSet<>();
            seen.add(query);
            seen.addAll(variants);
            return new ArrayList<>(seen);
        } catch (Exception e) {
            log.warn("LLM query rewrite failed for '{}': {}", query, e.getMessage());
            return List.of(query);
        }
    }

    private List<String> extractJsonArray(String json) {
        int s = json.indexOf("[");
        if (s < 0) return List.of();
        int e = json.lastIndexOf("]");
        if (e < 0) return List.of();
        String arr = json.substring(s + 1, e);
        List<String> items = new ArrayList<>();
        int pos = 0;
        while (pos < arr.length()) {
            int q1 = arr.indexOf("\"", pos);
            if (q1 < 0) break;
            int q2 = arr.indexOf("\"", q1 + 1);
            if (q2 < 0) break;
            String item = arr.substring(q1 + 1, q2).trim();
            if (!item.isEmpty()) items.add(item);
            pos = q2 + 1;
        }
        return items;
    }
}
