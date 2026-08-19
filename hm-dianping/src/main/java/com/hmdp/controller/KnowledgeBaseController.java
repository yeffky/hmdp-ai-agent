package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.rag.ingestion.IngestionService;
import com.hmdp.rag.model.DocumentChunk;
import com.hmdp.rag.model.IngestionRequest;
import com.hmdp.rag.model.KnowledgeBaseStats;
import com.hmdp.rag.model.SearchResult;
import com.hmdp.rag.retrieval.RetrievalService;
import com.hmdp.rag.splitter.AdaptiveSplitter;
import com.hmdp.rag.store.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库管理控制器 — 文档摄取、统计、删除。
 */
@RestController
@RequestMapping("/kb")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    @Resource
    private IngestionService ingestionService;

    @Resource
    private QdrantVectorStore qdrantVectorStore;

    @Resource
    private RetrievalService retrievalService;

    @Resource
    private AdaptiveSplitter adaptiveSplitter;

    /** 摄取一篇文档到知识库 */
    @PostMapping("/ingest")
    public Result ingest(@RequestBody IngestionRequest request) {
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.fail("文档内容不能为空");
        }
        if (request.getSource() == null || request.getSource().trim().isEmpty()) {
            return Result.fail("来源类型不能为空");
        }
        try {
            int count = ingestionService.ingest(
                    request.getContent(),
                    request.getSource(),
                    request.getTitle() != null ? request.getTitle() : "未命名文档");
            if (count == 0) {
                return Result.ok("文档内容重复或切片为空，已跳过摄入");
            }
            return Result.ok("成功摄入 " + count + " 个切片");
        } catch (Exception e) {
            log.error("知识库摄入失败", e);
            return Result.fail("摄入失败: " + e.getMessage());
        }
    }

    /** 知识库统计 */
    @GetMapping("/stats")
    public Result stats() {
        try {
            long total = qdrantVectorStore.countPoints();
            Map<String, Object> info = qdrantVectorStore.getCollectionInfo();
            KnowledgeBaseStats stats = new KnowledgeBaseStats(
                    (String) info.getOrDefault("name", "hmdp_knowledge"), total);
            return Result.ok(stats);
        } catch (Exception e) {
            log.error("获取知识库统计失败", e);
            return Result.fail("获取统计失败: " + e.getMessage());
        }
    }

    /** 按来源删除 */
    @DeleteMapping("/source/{source}")
    public Result deleteBySource(@PathVariable String source) {
        try {
            ingestionService.deleteBySource(source);
            return Result.ok("已删除 source=" + source + " 的数据");
        } catch (Exception e) {
            log.error("删除失败", e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /** 列出知识库中所有文档（按 source+title 聚合） */
    @GetMapping("/documents")
    public Result listDocuments() {
        try {
            List<DocumentChunk> all = qdrantVectorStore.scrollAll();
            Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
            for (DocumentChunk c : all) {
                String key = (c.getSource() != null ? c.getSource() : "") + "|" +
                             (c.getTitle() != null ? c.getTitle() : "");
                if (!groups.containsKey(key)) {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("source", c.getSource());
                    doc.put("title", c.getTitle());
                    doc.put("chunkCount", 0);
                    doc.put("preview", c.getText() != null && c.getText().length() > 200
                            ? c.getText().substring(0, 200) + "..." : c.getText());
                    Map<String, Object> meta = c.getMetadata();
                    doc.put("contentHash", meta != null && meta.containsKey("content_hash")
                            ? meta.get("content_hash").toString().substring(0, 12) : "");
                    groups.put(key, doc);
                }
                Map<String, Object> doc = groups.get(key);
                doc.put("chunkCount", ((Integer) doc.get("chunkCount")) + 1);
            }
            return Result.ok(new ArrayList<>(groups.values()));
        } catch (Exception e) {
            log.error("列出文档失败", e);
            return Result.fail("获取文档列表失败: " + e.getMessage());
        }
    }

    /** 获取单篇文档的所有切片详情 */
    @GetMapping("/documents/chunks")
    public Result listChunks(@RequestParam String source, @RequestParam String title) {
        try {
            List<DocumentChunk> all = qdrantVectorStore.scrollAll();
            List<Map<String, Object>> chunks = new ArrayList<>();
            for (DocumentChunk c : all) {
                if (!Objects.equals(c.getSource(), source) || !Objects.equals(c.getTitle(), title)) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.getId());
                m.put("text", c.getText());
                m.put("chunkIndex", c.getChunkIndex());
                Map<String, Object> meta = c.getMetadata();
                m.put("headingPath", meta != null ? meta.getOrDefault("headingPath", "") : "");
                m.put("length", c.getText() != null ? c.getText().length() : 0);
                chunks.add(m);
            }
            chunks.sort(Comparator.comparingInt(c -> ((Integer) c.get("chunkIndex"))));
            return Result.ok(chunks);
        } catch (Exception e) {
            log.error("获取切片列表失败", e);
            return Result.fail("获取切片失败: " + e.getMessage());
        }
    }

    /** 删除单篇文档 */
    @DeleteMapping("/document")
    public Result deleteDocument(@RequestParam String source, @RequestParam String title) {
        try {
            boolean ok = ingestionService.deleteBySourceAndTitle(source, title);
            return ok ? Result.ok("已删除 " + title) : Result.fail("删除失败");
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Result health() {
        try {
            long count = qdrantVectorStore.countPoints();
            return Result.ok("RAG 知识库正常，当前共 " + count + " 条向量");
        } catch (Exception e) {
            return Result.fail("知识库不可用: " + e.getMessage());
        }
    }

    /** 检索测试 — 直接返回检索到的文档片段 */
    @GetMapping("/search")
    public Result search(@RequestParam("q") String q,
                         @RequestParam(value = "topK", defaultValue = "5") int topK) {
        if (q == null || q.trim().isEmpty()) return Result.fail("查询不能为空");
        try {
            List<SearchResult> results = retrievalService.search(q, topK);
            List<Map<String, Object>> list = results.stream().map(r -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("text", r.getChunk().getText());
                m.put("source", r.getChunk().getSource());
                m.put("title", r.getChunk().getTitle());
                m.put("score", Math.round(r.getScore() * 10000.0) / 10000.0);
                return m;
            }).collect(Collectors.toList());
            return Result.ok(list);
        } catch (Exception e) {
            log.error("检索失败", e);
            return Result.fail("检索失败: " + e.getMessage());
        }
    }

    /** 切片预览 — 展示自适应切片后的结构和 headingPath */
    @PostMapping("/preview-split")
    public Result previewSplit(@RequestBody IngestionRequest request) {
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.fail("文档内容不能为空");
        }
        try {
            List<AdaptiveSplitter.ChunkResult> results = adaptiveSplitter.split(request.getContent());
            List<Map<String, Object>> chunks = new ArrayList<>();
            for (AdaptiveSplitter.ChunkResult r : results) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("index", r.getIndex());
                m.put("headingPath", r.getHeadingPath());
                m.put("text", r.getText());
                m.put("length", r.getText().length());
                chunks.add(m);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalChunks", chunks.size());
            summary.put("chunks", chunks);
            return Result.ok(summary);
        } catch (Exception e) {
            log.error("切片预览失败", e);
            return Result.fail("切片失败: " + e.getMessage());
        }
    }
}
