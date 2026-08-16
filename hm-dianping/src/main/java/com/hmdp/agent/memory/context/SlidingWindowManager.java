package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.state.ReActAgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动窗口管理器 — 集成压缩到图状态流中。
 * 在 ContextNode 调用，返回增量状态更新。
 */
@Component
public class SlidingWindowManager {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowManager.class);

    @Resource
    private ContextCompressor compressor;

    @Resource
    private CompressionConfig config;

    /** 无条件硬上限：checkpoint 中最多保留的消息条数（约 50 条，含 tool 轨迹），防止 JSON 膨胀拖慢反序列化 */
    private static final int HARD_MESSAGE_CAP = 50;

    /** 异步压缩结果的摘要缓存（userId → 最新摘要），供下一次上下文构建使用，不阻塞图流程 */
    private final Map<Long, String> asyncSummaries = new ConcurrentHashMap<>();

    /**
     * 对当前 Agent 状态执行上下文管理。
     * 返回增量更新的 Map，LangGraph4j 会通过 Channel/Reducer 合并。
     *
     * @param state 当前图状态
     * @return 增量更新 Map（messages 裁剪 + compressedSummary 更新）
     */
    public Map<String, Object> manageContext(ReActAgentState state) {
        Map<String, Object> updates = new LinkedHashMap<>();

        // 获取当前消息列表（使用自定义 concat channel，返回 List）
        List<Map<String, String>> msgList = state.messages();
        if (msgList == null || msgList.isEmpty()) {
            return updates;
        }
        String currentSummary = state.compressedSummary();

        // ================================================================
        // 无条件硬上限：防止 checkpoint JSON 无限膨胀导致反序列化越来越慢
        // ================================================================
        if (msgList.size() > HARD_MESSAGE_CAP) {
            int trimCount = msgList.size() - HARD_MESSAGE_CAP;
            List<Map<String, String>> trimmed = new ArrayList<>(
                    msgList.subList(trimCount, msgList.size()));
            updates.put("messages", trimmed);
            log.info("Hard cap: trimmed {} messages → kept {} (max {})",
                    trimCount, trimmed.size(), HARD_MESSAGE_CAP);
            // 继续走压缩逻辑（用裁剪后的列表），但如果压缩关闭也到此为止
            if (!config.isEnabled()) {
                return updates;
            }
            // 用裁剪后的列表继续
            msgList = trimmed;
        }

        // 硬上限检查（压缩关闭或未触发时的兜底）
        if (!config.isEnabled() && msgList.size() > config.getMaxUncompressedMessages()) {
            int trimCount = msgList.size() - config.getMaxUncompressedMessages();
            List<Map<String, String>> trimmed = new ArrayList<>(
                    msgList.subList(trimCount, msgList.size()));
            updates.put("messages", trimmed);
            log.debug("Hard cap (compression off): trimmed {} messages, kept {}", trimCount, trimmed.size());
            return updates;
        }

        // 若有上一次异步压缩的摘要，优先作为本轮上下文摘要（不阻塞、不等待 LLM）
        Long uid = userId(state);
        String asyncSum = uid != null ? asyncSummaries.get(uid) : null;
        if (asyncSum != null && !asyncSum.equals(currentSummary)) {
            updates.put("compressedSummary", asyncSum);
            currentSummary = asyncSum;
            log.info("Using async compressed summary ({} chars) for uid={}", asyncSum.length(), uid);
        }

        // 异步压缩：LLM 摘要 + 画像提取放到后台线程，不阻塞图流程（context 节点立即返回，前端有反馈）。
        // 结果缓存到 asyncSummaries，下一次上下文构建时生效。
        if (config.isEnabled()) {
            final List<Map<String, String>> msgs = new ArrayList<>(msgList);
            final String prevSummary = currentSummary;
            final Long fuid = uid;
            CompletableFuture.runAsync(() -> {
                try {
                    ContextCompressor.CompressionResult r = compressor.compressIfNeeded(msgs, prevSummary, fuid);
                    if (r.getSummary() != null && !r.getSummary().isEmpty() && fuid != null) {
                        asyncSummaries.put(fuid, r.getSummary());
                        log.info("Async compression done: {} chars for uid={}", r.getSummary().length(), fuid);
                    }
                } catch (Exception ex) {
                    log.warn("Async compression failed: {}", ex.getMessage());
                }
            });
        }

        return updates;
    }

    /** 从 state 中提取 userId（安全转换） */
    private Long userId(ReActAgentState state) {
        try {
            Object val = state.data().get("userId");
            if (val instanceof Number) return ((Number) val).longValue();
            if (val instanceof String) return Long.parseLong((String) val);
        } catch (Exception ignored) {}
        return null;
    }
}
