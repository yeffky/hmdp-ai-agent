package com.hmdp.rag.embedding;

import java.util.List;

/**
 * Embedding 服务接口 — 将文本转换为向量。
 */
public interface EmbeddingService {

    /**
     * 将单段文本转为向量
     */
    float[] embed(String text);

    /**
     * 批量向量化（减少 API 调用次数）
     */
    List<float[]> embedBatch(List<String> texts);

    /** 返回向量维度 */
    int dimension();
}
