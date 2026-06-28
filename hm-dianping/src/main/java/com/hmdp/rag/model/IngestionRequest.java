package com.hmdp.rag.model;

/**
 * 文档摄取请求 DTO
 */
public class IngestionRequest {

    private String content;
    private String source;
    private String title;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
