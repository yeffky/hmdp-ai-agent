package com.hmdp.rag.model;

import java.util.Map;

/**
 * 文档切片 — RAG 知识库的最小存储单元。
 * 每个 chunk 包含一段原文 + 向量 + 元数据，存储在 Qdrant 中。
 */
public class DocumentChunk {

    /** Qdrant 中的 point ID（UUID 字符串） */
    private String id;

    /** 切片文本内容 */
    private String text;

    /** 来源标识：shop / faq / blog / manual */
    private String source;

    /** 来源文档标题或名称 */
    private String title;

    /** 在原始文档中的切片序号（从 0 开始） */
    private Integer chunkIndex;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** 向量（embedding），检索时填充 */
    private float[] vector;

    public DocumentChunk() {}

    public DocumentChunk(String id, String text, String source, String title, Integer chunkIndex) {
        this.id = id;
        this.text = text;
        this.source = source;
        this.title = title;
        this.chunkIndex = chunkIndex;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }

    @Override
    public String toString() {
        return "DocumentChunk{id='" + id + "', source='" + source + "', title='" + title + "'}";
    }
}
