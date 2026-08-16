package com.hmdp.agent.memory.context;

import com.hmdp.agent.config.MemoryProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 压缩配置门面 — 从 MemoryProperties 中提取压缩相关配置，
 * 提供便捷的访问方法。
 */
@Component
public class CompressionConfig {

    @Resource
    private MemoryProperties memoryProperties;

    private MemoryProperties.Compression props;

    @PostConstruct
    private void init() {
        this.props = memoryProperties.getCompression();
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public int getCompressThreshold() {
        return props.getCompressThreshold();
    }

    public int getKeepRecentTokens() {
        return props.getKeepRecentTokens();
    }

    /** 保留最近 N 轮（真实 user 边界，含 tool 轨迹）不压缩，更早轮次压缩。 */
    public int getKeepRecentRounds() {
        return props.getKeepRecentRounds();
    }

    public int getMaxSummaryTokens() {
        return props.getMaxSummaryTokens();
    }

    public int getMaxUncompressedMessages() {
        return props.getMaxUncompressedMessages();
    }
}
