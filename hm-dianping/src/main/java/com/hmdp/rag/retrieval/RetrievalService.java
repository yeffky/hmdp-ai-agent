package com.hmdp.rag.retrieval;

import com.hmdp.rag.embedding.EmbeddingService;
import com.hmdp.rag.model.DocumentChunk;
import com.hmdp.rag.model.SearchResult;
import com.hmdp.rag.store.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索服务 — 多路向量检索 + RRF 融合。
 *
 * <h3>召回管线</h3>
 * <ol>
 *   <li>LLM 查询改写 → 生成 N 个变体</li>
 *   <li>每个变体独立向量检索 → N 路结果</li>
 *   <li>RRF (Reciprocal Rank Fusion) 融合去重排序</li>
 *   <li>返回 Top-K 结果</li>
 * </ol>
 *
 * <p>RRF 公式: score(d) = Σ 1/(k + rank_i(d)), k=60
 */
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private static final double RRF_K = 60.0;

    private final EmbeddingService embedding;
    private final QdrantVectorStore store;
    private final LLMQueryRewriter rewriter;
    private final int topK;
    private final double scoreThreshold;

    public RetrievalService(EmbeddingService embedding, QdrantVectorStore store,
                            LLMQueryRewriter rewriter,
                            int topK, double scoreThreshold) {
        this.embedding = embedding;
        this.store = store;
        this.rewriter = rewriter;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * 多路检索：LLM 改写 → 多路向量检索 → RRF 融合。
     */
    public List<SearchResult> search(String query) {
        // 1. LLM 改写查询
        List<String> variantQueries = rewriter.rewrite(query);
        log.info("Query rewritten to {} variants: {}", variantQueries.size(), variantQueries);

        // 2. 每路独立向量检索
        List<List<SearchResult>> allResultLists = new ArrayList<>();
        for (String variant : variantQueries) {
            List<SearchResult> results = singleVectorSearch(variant);
            if (!results.isEmpty()) {
                allResultLists.add(results);
            }
        }

        if (allResultLists.isEmpty()) {
            return Collections.emptyList();
        }

        // 单路直接返回
        if (allResultLists.size() == 1) {
            return allResultLists.get(0);
        }

        // 3. RRF 融合
        return rrfFusion(allResultLists);
    }

    /** 单路向量检索 */
    private List<SearchResult> singleVectorSearch(String query) {
        float[] queryVector = embedding.embed(query);
        if (queryVector.length == 0) {
            log.warn("查询向量化失败: {}", query);
            return Collections.emptyList();
        }
        // 每路多取一些，给 RRF 更多候选
        return store.search(queryVector, topK * 2, scoreThreshold);
    }

    /** RRF 融合多路结果 */
    private List<SearchResult> rrfFusion(List<List<SearchResult>> allResults) {
        // chunkId -> accumulated RRF score
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, SearchResult> bestHit = new HashMap<>();

        for (List<SearchResult> resultList : allResults) {
            for (int rank = 0; rank < resultList.size(); rank++) {
                SearchResult sr = resultList.get(rank);
                String id = sr.getChunk().getId();
                if (id == null) id = String.valueOf(Objects.hash(
                        sr.getChunk().getText(), sr.getChunk().getSource()));
                double rrf = 1.0 / (RRF_K + rank + 1); // rank 从 0 开始，RRF 用 1-indexed
                rrfScores.merge(id, rrf, Double::sum);
                // 保留 score 最高的那个副本
                if (!bestHit.containsKey(id) || sr.getScore() > bestHit.get(id).getScore()) {
                    bestHit.put(id, sr);
                }
            }
        }

        // 按 RRF 分数降序排列
        List<SearchResult> fused = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    SearchResult sr = bestHit.get(e.getKey());
                    sr.setRrfScore(e.getValue());
                    return sr;
                })
                .collect(Collectors.toList());

        log.info("RRF fusion: {} lists -> {} unique results, returning top {}",
                allResults.size(), rrfScores.size(), fused.size());
        return fused;
    }

    // ========== 兼容旧接口 ==========

    public String searchAsContext(String query) {
        List<SearchResult> results = search(query);
        if (results.isEmpty()) return "";
        return formatContext(results);
    }

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

    public String formatContextCompact(List<SearchResult> results) {
        return results.stream()
                .map(r -> r.getChunk().getText())
                .collect(Collectors.joining("\n\n"));
    }
}
