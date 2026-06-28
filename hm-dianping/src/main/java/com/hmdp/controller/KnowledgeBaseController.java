package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.rag.ingestion.IngestionService;
import com.hmdp.rag.model.IngestionRequest;
import com.hmdp.rag.model.KnowledgeBaseStats;
import com.hmdp.rag.model.SearchResult;
import com.hmdp.rag.retrieval.RetrievalService;
import com.hmdp.rag.splitter.MarkdownSplitter;
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
    private MarkdownSplitter markdownSplitter;

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
            List<SearchResult> results = retrievalService.search(q);
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

    /** 切片预览 — 展示 Markdown/文本 切分后的结构 */
    @PostMapping("/preview-split")
    public Result previewSplit(@RequestBody IngestionRequest request) {
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.fail("文档内容不能为空");
        }
        try {
            List<Map<String, Object>> chunks = new ArrayList<>();
            if (request.getContent().trim().startsWith("#")) {
                // Markdown 结构感知切片
                List<MarkdownSplitter.ChunkWithHeadings> result = markdownSplitter.split(request.getContent());
                for (MarkdownSplitter.ChunkWithHeadings c : result) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("index", c.getIndex());
                    m.put("headingPath", c.getHeadingPath());
                    m.put("text", c.getText());
                    m.put("length", c.getText().length());
                    chunks.add(m);
                }
            } else {
                // 通用文本切片（仅显示文本）
                List<String> texts = markdownSplitter.splitPlain(request.getContent());
                for (int i = 0; i < texts.size(); i++) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("index", i);
                    m.put("headingPath", "");
                    m.put("text", texts.get(i));
                    m.put("length", texts.get(i).length());
                    chunks.add(m);
                }
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
