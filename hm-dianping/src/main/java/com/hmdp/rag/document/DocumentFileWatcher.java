package com.hmdp.rag.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 文档目录监控器 — WatchService 实时监控 + 定时全量兜底。
 *
 * <p>防抖：同一文件在 debounceSeconds 内的重复事件合并为单次处理。
 * <p>定时扫描：处理应用离线期间发生的文件变更。
 */
@Component
@ConditionalOnProperty(name = "rag.document.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentFileWatcher.class);

    private final DocumentPipeline pipeline;
    private final Path watchDir;
    private final int debounceSeconds;
    private final ConcurrentHashMap<String, Long> lastEventTime = new ConcurrentHashMap<>();
    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "doc-watcher");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "doc-worker");
        t.setDaemon(true);
        return t;
    });
    private volatile WatchService watchService;
    private volatile boolean running = false;

    public DocumentFileWatcher(DocumentPipeline pipeline,
                               @Value("${rag.document.watch-dir:./rag-documents}") String watchDir,
                               @Value("${rag.document.debounce-seconds:5}") int debounceSeconds) {
        this.pipeline = pipeline;
        this.watchDir = Path.of(watchDir).toAbsolutePath();
        this.debounceSeconds = debounceSeconds;
    }

    @PostConstruct
    void start() {
        try {
            Files.createDirectories(watchDir);
            watchService = FileSystems.getDefault().newWatchService();
            watchDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            running = true;
            watcherExecutor.submit(this::watchLoop);
            log.info("文档监控已启动: {}", watchDir);

            // 启动时全量处理已有文件
            pipeline.processAll(watchDir);
        } catch (Exception e) {
            log.error("启动文档监控失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        watcherExecutor.shutdownNow();
        workerExecutor.shutdownNow();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception ignored) {}
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("WatchService 异常: {}", e.getMessage());
                continue;
            }
            if (key == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                Path fileName = (Path) event.context();
                Path fullPath = watchDir.resolve(fileName);

                if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    log.info("检测到文件删除: {}", fileName);
                    pipeline.removeFile(fullPath);
                    lastEventTime.remove(fullPath.toAbsolutePath().toString());
                } else {
                    // CREATE / MODIFY → 防抖
                    onFileChanged(fullPath);
                }
            }
            key.reset();
        }
    }

    private void onFileChanged(Path filePath) {
        String absPath = filePath.toAbsolutePath().toString();
        long now = System.currentTimeMillis();
        lastEventTime.put(absPath, now);
        log.info("检测到文件变更: {}", filePath.getFileName());
        // 每个事件都提交延迟任务，只有最后一个任务会实际执行
        workerExecutor.submit(() -> {
            try {
                Thread.sleep(debounceSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long latest = lastEventTime.getOrDefault(absPath, 0L);
            if (latest > now) {
                return; // 有更新的变更事件，跳过
            }
            pipeline.processFile(filePath);
        });
    }

    /** 定时全量扫描：处理应用离线期间的变更 */
    @Scheduled(fixedDelayString = "${rag.document.full-scan-interval-seconds:300}000")
    void fullScan() {
        log.debug("定时全量扫描: {}", watchDir);
        pipeline.processAll(watchDir);
    }
}
