package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.state.ReActAgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

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

        // 硬上限检查（压缩关闭或未触发时的兜底）
        if (!config.isEnabled() && msgList.size() > config.getMaxUncompressedMessages()) {
            int trimCount = msgList.size() - config.getMaxUncompressedMessages();
            List<Map<String, String>> trimmed = new ArrayList<>(
                    msgList.subList(trimCount, msgList.size()));
            updates.put("messages", trimmed);
            log.debug("Hard cap: trimmed {} messages, kept {}", trimCount, trimmed.size());
            return updates;
        }

        // 执行压缩检查（传入 userId 用于画像提取）
        ContextCompressor.CompressionResult result =
                compressor.compressIfNeeded(msgList, currentSummary, userId(state));

        // 如果摘要变化，更新
        if (result.getSummary() != null && !result.getSummary().equals(currentSummary)) {
            updates.put("compressedSummary", result.getSummary());
            log.info("Compressed summary updated ({} chars)", result.getSummary().length());
        }

        // 如果消息被截断，替换消息列表
        if (result.getKeptMessages().size() < msgList.size()) {
            updates.put("messages", result.getKeptMessages());
            log.info("Messages trimmed from {} to {} after compression",
                    msgList.size(), result.getKeptMessages().size());
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
