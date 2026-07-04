package com.hmdp.rag.ingestion;

import com.hmdp.rag.embedding.EmbeddingService;
import com.hmdp.rag.model.DocumentChunk;
import com.hmdp.rag.retrieval.BM25KeywordIndex;
import com.hmdp.rag.splitter.AdaptiveSplitter;
import com.hmdp.rag.store.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文档摄取服务 — 自适应切片 → 向量化 → 存入 Qdrant。
 * 内置 SHA256 内容去重：相同内容的文档跳过摄入。
 */
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private static final String PAYLOAD_KEY_HASH = "content_hash";

    private final AdaptiveSplitter adaptiveSplitter;
    private final EmbeddingService embedding;
    private final QdrantVectorStore store;
    private final BM25KeywordIndex bm25Index;

    /** content SHA256 → "source:title"，用于快速去重 */
    private final Set<String> ingestedHashes = ConcurrentHashMap.newKeySet();
    private volatile boolean hashesLoaded;

    public IngestionService(AdaptiveSplitter adaptiveSplitter,
                            EmbeddingService embedding, QdrantVectorStore store,
                            BM25KeywordIndex bm25Index) {
        this.adaptiveSplitter = adaptiveSplitter;
        this.embedding = embedding;
        this.store = store;
        this.bm25Index = bm25Index;
    }

    // ========== 哈希工具 ==========

    static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void ensureHashesLoaded() {
        if (hashesLoaded) return;
        synchronized (this) {
            if (hashesLoaded) return;
            List<DocumentChunk> all = store.scrollAll();
            for (DocumentChunk c : all) {
                Map<String, Object> meta = c.getMetadata();
                if (meta != null && meta.containsKey(PAYLOAD_KEY_HASH)) {
                    ingestedHashes.add(meta.get(PAYLOAD_KEY_HASH).toString());
                }
            }
            hashesLoaded = true;
            log.info("去重哈希已加载：{} 个文档", ingestedHashes.size());
        }
    }

    /** 删除源后重建哈希集合（从 Qdrant 全量重载） */
    private void reloadHashes() {
        ingestedHashes.clear();
        hashesLoaded = false;
        ensureHashesLoaded();
    }

    // ========== 公开 API ==========

    /**
     * 摄取文档到知识库。返回成功摄入的切片数量，重复文档返回 0。
     */
    public int ingest(String content, String source, String title) {
        ensureHashesLoaded();

        String contentHash = sha256(content);
        // 原子 check-and-add，消除 TOCTOU 竞态条件导致的双写
        if (!ingestedHashes.add(contentHash)) {
            log.info("文档 [{}] 内容重复（hash={}），跳过摄入", title, contentHash.substring(0, 12));
            return 0;
        }

        try {
            List<AdaptiveSplitter.ChunkResult> results = adaptiveSplitter.split(content);
            if (results.isEmpty()) {
                log.warn("文档 {} 切片为空，跳过", title);
                ingestedHashes.remove(contentHash);
                return 0;
            }
            log.info("文档 [{}] 自适应切分为 {} 个片段", title, results.size());

            List<ChunkData> items = results.stream()
                    .map(r -> new ChunkData(r.getText(), source, title, r.getHeadingPath(), r.getIndex()))
                    .collect(Collectors.toList());
            List<DocumentChunk> chunks = embedAndAssemble(items, contentHash);
            if (chunks.isEmpty()) {
                ingestedHashes.remove(contentHash);
                return 0;
            }

            store.upsert(chunks);
            bm25Index.rebuild(store.scrollAll());
            log.info("摄入完成 [{}]：{} 个切片，BM25 索引已更新", title, chunks.size());
            return chunks.size();
        } catch (Exception e) {
            ingestedHashes.remove(contentHash);
            throw e;
        }
    }

    /** 批量向量化 + 装配 */
    private List<DocumentChunk> embedAndAssemble(List<ChunkData> items, String contentHash) {
        List<String> texts = items.stream().map(c -> c.text).collect(Collectors.toList());
        List<float[]> embeddings = embedding.embedBatch(texts);
        if (embeddings.isEmpty() || embeddings.size() != texts.size()) {
            log.error("向量化失败：期望 {} 个向量，实际 {} 个", texts.size(), embeddings.size());
            return Collections.emptyList();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ChunkData cd = items.get(i);
            // 用 contentHash+index 生成确定性 UUID，多实例并发 upsert 时天然幂等
            String deterministicId = UUID.nameUUIDFromBytes(
                    (contentHash + "-" + cd.index).getBytes(StandardCharsets.UTF_8)).toString();
            DocumentChunk chunk = new DocumentChunk(
                    deterministicId,
                    cd.text, cd.source, cd.title, cd.index);
            chunk.setVector(embeddings.get(i));
            Map<String, Object> meta = new HashMap<>();
            meta.put(PAYLOAD_KEY_HASH, contentHash);
            if (cd.headingPath != null && !cd.headingPath.isEmpty()) {
                meta.put("headingPath", cd.headingPath);
            }
            chunk.setMetadata(meta);
            chunks.add(chunk);
        }
        return chunks;
    }

    // ========== 批量 ==========

    public int ingestBatch(List<IngestionItem> items) {
        int total = 0;
        for (IngestionItem item : items) {
            int n = ingest(item.content, item.source, item.title);
            if (n > 0) total += n;
        }
        log.info("批量摄取完成：{} 篇文档，共 {} 个切片", items.size(), total);
        return total;
    }

    public int rebuild(List<IngestionItem> items) {
        store.deleteCollection();
        store.ensureCollection();
        ingestedHashes.clear();
        hashesLoaded = true; // 空集合视为已加载
        return ingestBatch(items);
    }

    public boolean deleteBySource(String source) {
        boolean ok = store.deleteBySource(source);
        if (ok) {
            bm25Index.rebuild(store.scrollAll());
            reloadHashes();
        }
        return ok;
    }

    public boolean deleteBySourceAndTitle(String source, String title) {
        boolean ok = store.deleteBySourceAndTitle(source, title);
        if (ok) {
            bm25Index.rebuild(store.scrollAll());
            reloadHashes();
        }
        return ok;
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
