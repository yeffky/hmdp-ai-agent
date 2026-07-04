package com.hmdp.rag.rerank;

import com.hmdp.rag.model.SearchResult;

import java.util.List;

/** Rerank 重排序接口 — 对 RRF 融合后的候选集进行二次精排。 */
public interface Reranker {

    /**
     * 对候选结果重排序。
     *
     * @param query      原始查询
     * @param candidates RRF 融合后的候选集
     * @param topN       返回条数
     * @return 重排后的 topN 结果
     */
    List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN);
}
