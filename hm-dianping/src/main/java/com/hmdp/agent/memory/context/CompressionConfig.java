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

    public int getMaxSummaryTokens() {
        return props.getMaxSummaryTokens();
    }

    public int getMaxUncompressedMessages() {
        return props.getMaxUncompressedMessages();
    }
}
