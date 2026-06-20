package com.hmdp.rag.model;

/**
 * 检索结果 — 从向量库检索返回的文档切片 + 相似度分数。
 */
public class SearchResult {

    private DocumentChunk chunk;
    private Double score;

    public SearchResult() {}

    public SearchResult(DocumentChunk chunk, Double score) {
        this.chunk = chunk;
        this.score = score;
    }

    public DocumentChunk getChunk() { return chunk; }
    public void setChunk(DocumentChunk chunk) { this.chunk = chunk; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    /** 格式化为 LLM 可读的上下文片段 */
    public String toContextString() {
        return String.format("[来源: %s, 相关度: %.2f]\n%s",
                chunk.getTitle() != null ? chunk.getTitle() : chunk.getSource(),
                score, chunk.getText());
    }

    @Override
    public String toString() {
        return "SearchResult{score=" + score + ", chunk=" + chunk + "}";
    }
}
