package com.hmdp.rag.ingestion;

import com.hmdp.rag.embedding.EmbeddingService;
import com.hmdp.rag.model.DocumentChunk;
import com.hmdp.rag.splitter.DocumentSplitter;
import com.hmdp.rag.splitter.MarkdownSplitter;
import com.hmdp.rag.store.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档摄取服务 — 将文档从原始文本变成向量库中可检索的切片。
 *
 * 完整管线：文档 → 切片（支持 Markdown 结构感知） → 向量化 → 存入 Qdrant
 */
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentSplitter simpleSplitter;
    private final MarkdownSplitter markdownSplitter;
    private final EmbeddingService embedding;
    private final QdrantVectorStore store;

    public IngestionService(DocumentSplitter simpleSplitter, MarkdownSplitter markdownSplitter,
                            EmbeddingService embedding, QdrantVectorStore store) {
        this.simpleSplitter = simpleSplitter;
        this.markdownSplitter = markdownSplitter;
        this.embedding = embedding;
        this.store = store;
    }

    /**
     * 摄取纯文本/Markdown 文档到知识库。
     * 自动检测：以 # 开头则使用 Markdown 结构感知切片，否则使用通用切片。
     *
     * @return 成功摄入的切片数量
     */
    private static final java.util.regex.Pattern MD_PATTERN = java.util.regex.Pattern.compile(
            "^#\\s|\\n#+\\s|!\\[|\\n\\|.*\\||```\\w*\\n", java.util.regex.Pattern.MULTILINE);

    public int ingest(String content, String source, String title) {
        List<DocumentChunk> chunks;
        if (MD_PATTERN.matcher(content).find()) {
            chunks = ingestMarkdown(content, source, title);
        } else {
            chunks = ingestPlain(content, source, title);
        }
        if (chunks.isEmpty()) {
            log.warn("文档 {} 切片为空，跳过", title);
            return 0;
        }
        store.upsert(chunks);
        return chunks.size();
    }

    /** 使用 Markdown 结构感知切片 */
    private List<DocumentChunk> ingestMarkdown(String content, String source, String title) {
        List<MarkdownSplitter.ChunkWithHeadings> splitChunks = markdownSplitter.split(content);
        if (splitChunks.isEmpty()) return Collections.emptyList();
        log.info("Markdown 文档 [{}] 按结构切分为 {} 个片段", title, splitChunks.size());
        return embedAndAssemble(splitChunks.stream()
                        .map(c -> new ChunkData(c.text, source, title, c.headingPath, c.index))
                        .collect(Collectors.toList()));
    }

    /** 使用通用固定/递归切片 */
    private List<DocumentChunk> ingestPlain(String content, String source, String title) {
        List<DocumentSplitter.ChunkWithIndex> splitChunks = simpleSplitter.splitWithIndex(content, source, title);
        if (splitChunks.isEmpty()) return Collections.emptyList();
        log.info("纯文本文档 [{}] 切分为 {} 个片段", title, splitChunks.size());
        return embedAndAssemble(splitChunks.stream()
                .map(c -> new ChunkData(c.getText(), source, title, null, c.getIndex()))
                .collect(Collectors.toList()));
    }

    /** 批量向量化 + 装配 */
    private List<DocumentChunk> embedAndAssemble(List<ChunkData> items) {
        List<String> texts = items.stream().map(c -> c.text).collect(Collectors.toList());
        List<float[]> embeddings = embedding.embedBatch(texts);
        if (embeddings.isEmpty() || embeddings.size() != texts.size()) {
            log.error("向量化失败：期望 {} 个向量，实际 {} 个", texts.size(), embeddings.size());
            return Collections.emptyList();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ChunkData cd = items.get(i);
            DocumentChunk chunk = new DocumentChunk(
                    UUID.randomUUID().toString(),
                    cd.text, cd.source, cd.title, cd.index);
            chunk.setVector(embeddings.get(i));
            // 将 heading 路径存入 metadata
            if (cd.headingPath != null && !cd.headingPath.isEmpty()) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("headingPath", cd.headingPath);
                chunk.setMetadata(meta);
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    // ========== 批量 ==========

    public int ingestBatch(List<IngestionItem> items) {
        int total = 0;
        for (IngestionItem item : items) {
            total += ingest(item.content, item.source, item.title);
        }
        log.info("批量摄取完成：{} 篇文档，共 {} 个切片", items.size(), total);
        return total;
    }

    public int rebuild(List<IngestionItem> items) {
        store.deleteCollection();
        store.ensureCollection();
        return ingestBatch(items);
    }

    public boolean deleteBySource(String source) {
        return store.deleteBySource(source);
    }

    // ========== 内部类 ==========

    public static class IngestionItem {
        public final String content;
        public final String source;
        public final String title;

        public IngestionItem(String content, String source, String title) {
            this.content = content;
            this.source = source;
            this.title = title;
        }
    }

    /** 切片中间数据 */
    private static class ChunkData {
        final String text;
        final String source;
        final String title;
        final String headingPath;
        final int index;

        ChunkData(String text, String source, String title, String headingPath, int index) {
            this.text = text;
            this.source = source;
            this.title = title;
            this.headingPath = headingPath;
            this.index = index;
        }
    }
}
