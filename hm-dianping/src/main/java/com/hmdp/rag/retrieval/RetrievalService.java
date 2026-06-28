package com.hmdp.rag.retrieval;

import com.hmdp.rag.embedding.EmbeddingService;
import com.hmdp.rag.model.SearchResult;
import com.hmdp.rag.store.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索服务 — 用户查询 → 向量检索 → 返回相关文档片段。
 *
 * 完整管线：查询文本 → 向量化 → Qdrant 相似检索 → 格式化上下文
 */
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingService embedding;
    private final QdrantVectorStore store;
    private final int topK;
    private final double scoreThreshold;

    public RetrievalService(EmbeddingService embedding, QdrantVectorStore store,
                            int topK, double scoreThreshold) {
        this.embedding = embedding;
        this.store = store;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * 检索相关文档片段。
     *
     * @param query 用户查询文本
     * @return 按相关度降序排列的检索结果
     */
    public List<SearchResult> search(String query) {
        // 1. 查询向量化
        float[] queryVector = embedding.embed(query);
        if (queryVector.length == 0) {
            log.warn("查询向量化失败: {}", query);
            return Collections.emptyList();
        }

        // 2. 向量检索
        List<SearchResult> results = store.search(queryVector, topK, scoreThreshold);
        log.info("检索完成: query='{}', 命中 {} 条", query, results.size());
        return results;
    }

    /**
     * 检索并格式化为 LLM 上下文 Prompt。
     *
     * @param query 用户查询
     * @return 可直接拼接到 LLM prompt 中的上下文字符串
     */
    public String searchAsContext(String query) {
        List<SearchResult> results = search(query);
        if (results.isEmpty()) {
            return "";
        }
        return formatContext(results);
    }

    /**
     * 将检索结果格式化为 LLM 可读的上下文。
     */
    public String formatContext(List<SearchResult> results) {
        if (results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("# 参考知识库内容\n\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("## 参考资料 ").append(i + 1)
                    .append(" (来源: ").append(r.getChunk().getTitle())
                    .append(", 相关度: ").append(String.format("%.2f", r.getScore())).append(")\n");
            sb.append(r.getChunk().getText()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 简单地将检索结果拼接为纯文本（用于 token 紧张场景）。
     */
    public String formatContextCompact(List<SearchResult> results) {
        return results.stream()
                .map(r -> r.getChunk().getText())
                .collect(Collectors.joining("\n\n"));
    }
}
