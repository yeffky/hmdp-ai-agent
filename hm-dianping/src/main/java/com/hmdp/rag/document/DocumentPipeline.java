package com.hmdp.rag.document;

import com.hmdp.rag.ingestion.IngestionService;
import com.hmdp.rag.store.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档处理管线 — 编排 转换 → 哈希对比 → 删除旧向量 → 摄入 的完整流程。
 */
public class DocumentPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentPipeline.class);

    private final DocumentConverter converter;
    private final IngestionService ingestionService;
    private final QdrantVectorStore vectorStore;
    private final IngestionStateManager stateManager;
    private final Set<String> allowedExtensions;

    public DocumentPipeline(DocumentConverter converter,
                            IngestionService ingestionService,
                            QdrantVectorStore vectorStore,
                            IngestionStateManager stateManager,
                            Set<String> allowedExtensions) {
        this.converter = converter;
        this.ingestionService = ingestionService;
        this.vectorStore = vectorStore;
        this.stateManager = stateManager;
        this.allowedExtensions = allowedExtensions;
    }

    /**
     * 处理单个文件：转换 → 哈希对比 → 删旧 → 摄入 → 更新状态。
     */
    public void processFile(Path filePath) {
        String ext = getExtension(filePath);
        if (!allowedExtensions.contains(ext)) {
            log.debug("跳过不支持的文件类型: {}", filePath);
            return;
        }
        if (!Files.exists(filePath)) {
            log.debug("文件不存在，跳过: {}", filePath);
            return;
        }

        String absPath = filePath.toAbsolutePath().toString();
        Map<String, String> state = stateManager.loadState();

        // 哈希对比
        String newHash = IngestionStateManager.sha256(filePath);
        if (newHash.isEmpty()) {
            log.warn("无法计算文件哈希，跳过: {}", absPath);
            return;
        }
        String oldHash = state.get(absPath);
        if (newHash.equals(oldHash)) {
            log.info("文件未变更，跳过: {}", filePath.getFileName());
            return;
        }

        // 转换（markitdown CLI）
        String markdown;
        try {
            markdown = converter.convert(filePath);
        } catch (DocumentConverter.ConversionException e) {
            log.warn("文档转换失败: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("文档转换异常: {} — {}", filePath.getFileName(), e.getMessage());
            return;
        }

        // 删除旧向量
        if (oldHash != null) {
            vectorStore.deleteBySource(absPath);
        }

        // 摄入
        String title = filePath.getFileName().toString();
        int chunks = ingestionService.ingest(markdown, absPath, title);
        log.info("摄入完成: {} → {} 个切片", filePath.getFileName(), chunks);

        // 更新状态
        stateManager.updateState(absPath, newHash);
    }

    /** 处理目录下所有文件 */
    public int processAll(Path watchDir) {
        if (!Files.isDirectory(watchDir)) {
            log.warn("监控目录不存在: {}", watchDir);
            return 0;
        }
        try {
            var files = Files.list(watchDir)
                    .filter(Files::isRegularFile)
                    .filter(f -> allowedExtensions.contains(getExtension(f)))
                    .collect(Collectors.toList());
            log.info("全量扫描：发现 {} 个支持的文件", files.size());
            for (Path file : files) {
                processFile(file);
            }
            return files.size();
        } catch (Exception e) {
            log.error("全量扫描失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 删除文件对应的向量并清理状态 */
    public void removeFile(Path filePath) {
        String absPath = filePath.toAbsolutePath().toString();
        vectorStore.deleteBySource(absPath);
        stateManager.removeState(absPath);
        log.info("已删除文件对应的向量: {}", filePath.getFileName());
    }

    private static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    /** 从逗号分隔字符串解析扩展名集合 */
    public static Set<String> parseExtensions(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
