package com.hmdp.rag.retrieval;

import com.hmdp.rag.embedding.EmbeddingService;
import com.hmdp.rag.model.DocumentChunk;
import com.hmdp.rag.model.SearchResult;
import com.hmdp.rag.rerank.Reranker;
import com.hmdp.rag.store.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索服务 — 混合检索（语义向量 + BM25 关键词）+ RRF 融合 + Rerank 重排序。
 *
 * <h3>召回管线</h3>
 * <ol>
 *   <li>LLM 查询改写 → 生成 N 个变体</li>
 *   <li>每变体双路召回：语义向量检索 + BM25 关键词检索</li>
 *   <li>RRF (Reciprocal Rank Fusion) 融合去重排序</li>
 *   <li>LLM Rerank 重排序</li>
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
    private final BM25KeywordIndex bm25Index;
    private final Reranker reranker;
    private final int topK;
    private final double scoreThreshold;

    public RetrievalService(EmbeddingService embedding, QdrantVectorStore store,
                            LLMQueryRewriter rewriter,
                            BM25KeywordIndex bm25Index, Reranker reranker,
                            int topK, double scoreThreshold) {
        this.embedding = embedding;
        this.store = store;
        this.rewriter = rewriter;
        this.bm25Index = bm25Index;
        this.reranker = reranker;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * 混合检索：LLM 改写 → 语义向量 + BM25 双路召回 → RRF 融合 → Rerank 重排序。
     */
    public List<SearchResult> search(String query) {
        return search(query, topK);
    }

    public List<SearchResult> search(String query, int requestedTopK) {
        int resultTopK = Math.max(1, Math.min(requestedTopK, 50));
        // 1. LLM 改写查询
        List<String> variantQueries = rewriter.rewrite(query);
        log.info("Query rewritten to {} variants: {}", variantQueries.size(), variantQueries);

        // 2. 每变体双路召回：语义向量 + BM25 关键词
        List<List<SearchResult>> allResultLists = new ArrayList<>();
        for (String variant : variantQueries) {
            // 语义检索
            List<SearchResult> vecResults = singleVectorSearch(variant, resultTopK);
            if (!vecResults.isEmpty()) {
                allResultLists.add(vecResults);
            }
            // BM25 关键词检索
            List<SearchResult> kwResults = bm25Index.search(variant, resultTopK * 2);
            if (!kwResults.isEmpty()) {
                allResultLists.add(kwResults);
            }
        }

        if (allResultLists.isEmpty()) {
            return Collections.emptyList();
        }

        // 单路直接返回（跳过 RRF）
        if (allResultLists.size() == 1) {
            List<SearchResult> single = allResultLists.get(0);
            return single.size() > resultTopK ? single.subList(0, resultTopK) : single;
        }

        // 3. RRF 融合
        List<SearchResult> fused = rrfFusion(allResultLists, resultTopK);
        log.info("Hybrid retrieval: {} lists → RRF fused {} results",
                allResultLists.size(), fused.size());

        // 4. Rerank 重排序
        if (reranker != null && fused.size() > resultTopK) {
            fused = reranker.rerank(query, fused, resultTopK);
        }

        return fused.size() > resultTopK ? fused.subList(0, resultTopK) : fused;
    }

    /** 单路向量检索 */
    private List<SearchResult> singleVectorSearch(String query, int resultTopK) {
        float[] queryVector = embedding.embed(query);
        if (queryVector.length == 0) {
            log.warn("查询向量化失败: {}", query);
            return Collections.emptyList();
        }
        // 每路多取一些，给 RRF 更多候选
        return store.search(queryVector, resultTopK * 2, scoreThreshold);
    }

    /** RRF 融合多路结果 */
    private List<SearchResult> rrfFusion(List<List<SearchResult>> allResults, int resultTopK) {
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
                .limit(resultTopK)
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
