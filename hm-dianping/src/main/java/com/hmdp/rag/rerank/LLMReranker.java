package com.hmdp.rag.rerank;

import com.hmdp.rag.model.SearchResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM Reranker — 用 LLM 对候选文档进行语义相关性重排序。
 *
 * <p>将 query + 候选文本批量发给 LLM，LLM 按相关性排序后返回序号列表，
 * 据此重排候选集，返回 topN。
 */
public class LLMReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(LLMReranker.class);

    private final ChatModel model;

    public static final int MAX_CANDIDATES = 15;
    public static final int MAX_TEXT_LEN = 400;

    public LLMReranker(ChatModel model) {
        this.model = model;
    }

    @Override
    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (candidates.size() <= topN) return new ArrayList<>(candidates);

        int n = Math.min(candidates.size(), MAX_CANDIDATES);
        List<SearchResult> subset = candidates.subList(0, n);

        try {
            List<Integer> order = callLLM(query, subset);
            List<SearchResult> reranked = new ArrayList<>();
            for (int idx : order) {
                if (idx >= 0 && idx < subset.size()) {
                    reranked.add(subset.get(idx));
                }
            }
            // 追加 LLM 未提及的候选项（保底）
            Set<Integer> mentioned = new HashSet<>(order);
            for (int i = 0; i < subset.size(); i++) {
                if (!mentioned.contains(i)) reranked.add(subset.get(i));
            }

            int resultSize = Math.min(topN, reranked.size());
            log.info("Rerank: {} candidates → top {} (LLM returned {} ordered)",
                    subset.size(), resultSize, order.size());
            return reranked.subList(0, resultSize);
        } catch (Exception e) {
            log.warn("LLM rerank failed, returning original order: {}", e.getMessage());
            return candidates.subList(0, Math.min(topN, candidates.size()));
        }
    }

    private List<Integer> callLLM(String query, List<SearchResult> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("查询: ").append(query).append("\n\n");
        for (int i = 0; i < candidates.size(); i++) {
            String text = candidates.get(i).getChunk().getText();
            if (text != null && text.length() > MAX_TEXT_LEN) {
                text = text.substring(0, MAX_TEXT_LEN) + "...";
            }
            sb.append("[").append(i).append("] ").append(text).append("\n\n");
        }

        String prompt = sb.toString();
        log.debug("Rerank prompt: {} chars, {} candidates", prompt.length(), candidates.size());

        ChatResponse resp = model.chat(List.of(
                SystemMessage.from("你是文档重排序器。根据查询相关性，将候选文档从最相关到最不相关排序。" +
                        "只输出排序后的序号列表，如 [3, 0, 5, 1, 2, 4]。不要输出其他内容。"),
                UserMessage.from(prompt)));

        String raw = resp.aiMessage().text().trim();
        log.info("Rerank LLM output: {}", raw);
        return parseOrder(raw);
    }

    private List<Integer> parseOrder(String raw) {
        List<Integer> order = new ArrayList<>();
        for (char c : raw.toCharArray()) {
            if (c >= '0' && c <= '9') {
                order.add(c - '0');
            }
        }
        return order;
    }
}
