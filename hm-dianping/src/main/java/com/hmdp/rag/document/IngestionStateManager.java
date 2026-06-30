package com.hmdp.rag.document;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档摄入状态管理 — 基于 JSON 文件的 SHA-256 状态持久化。
 *
 * <p>核心职责：记录每个文档的最后摄入哈希，避免重复处理未变更文件。
 */
public class IngestionStateManager {

    private static final Logger log = LoggerFactory.getLogger(IngestionStateManager.class);

    private final Path stateFile;

    public IngestionStateManager(Path stateFile) {
        this.stateFile = stateFile;
    }

    /** 加载状态文件，返回 Map<文件绝对路径, SHA-256>。文件不存在则返回空 Map。 */
    public Map<String, String> loadState() {
        if (!Files.exists(stateFile)) {
            return new LinkedHashMap<>();
        }
        try {
            String raw = Files.readString(stateFile, StandardCharsets.UTF_8);
            if (raw.isBlank()) return new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, String> map = JSONUtil.toBean(raw, Map.class);
            return new LinkedHashMap<>(map);
        } catch (Exception e) {
            log.warn("读取状态文件失败，使用空状态: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 保存状态到文件 */
    public synchronized void saveState(Map<String, String> state) {
        try {
            Files.createDirectories(stateFile.getParent());
            String json = JSONUtil.toJsonPrettyStr(state);
            Files.writeString(stateFile, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("写入状态文件失败: {}", e.getMessage());
        }
    }

    /** 更新单个文件的状态（线程安全） */
    public synchronized void updateState(String absolutePath, String hash) {
        Map<String, String> state = loadState();
        state.put(absolutePath, hash);
        saveState(state);
    }

    /** 移除单个文件的状态 */
    public synchronized void removeState(String absolutePath) {
        Map<String, String> state = loadState();
        state.remove(absolutePath);
        saveState(state);
    }

    /** 新文件或内容变更返回 true */
    public boolean needsIngestion(Path filePath, Map<String, String> state) {
        String absPath = filePath.toAbsolutePath().toString();
        String oldHash = state.get(absPath);
        if (oldHash == null) return true;
        String newHash = sha256(filePath);
        return !oldHash.equals(newHash);
    }

    /** 计算文件 SHA-256 */
    public static String sha256(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(filePath));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            log.warn("计算文件哈希失败: {} — {}", filePath, e.getMessage());
            return "";
        }
    }
}
