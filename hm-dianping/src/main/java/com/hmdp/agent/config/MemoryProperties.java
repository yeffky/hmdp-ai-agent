package com.hmdp.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆/上下文管理相关配置。
 * 对应 application.yaml 中 agent.memory.*
 */
@Component
@ConfigurationProperties(prefix = "agent.memory")
public class MemoryProperties {

    private Compression compression = new Compression();

    // ========== 压缩配置 ==========
    public static class Compression {
        /** 是否启用压缩 */
        private boolean enabled = true;
        /** 总 token 超过此值触发压缩 */
        private int compressThreshold = 4000;
        /** 保留最近 N tokens 不压缩 */
        private int keepRecentTokens = 2000;
        /** 压缩摘要最大 token 数 */
        private int maxSummaryTokens = 500;
        /** 硬上限：最多保留的消息数（压缩关闭或未触发时的兜底） */
        private int maxUncompressedMessages = 50;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getCompressThreshold() { return compressThreshold; }
        public void setCompressThreshold(int compressThreshold) { this.compressThreshold = compressThreshold; }

        public int getKeepRecentTokens() { return keepRecentTokens; }
        public void setKeepRecentTokens(int keepRecentTokens) { this.keepRecentTokens = keepRecentTokens; }

        public int getMaxSummaryTokens() { return maxSummaryTokens; }
        public void setMaxSummaryTokens(int maxSummaryTokens) { this.maxSummaryTokens = maxSummaryTokens; }

        public int getMaxUncompressedMessages() { return maxUncompressedMessages; }
        public void setMaxUncompressedMessages(int maxUncompressedMessages) { this.maxUncompressedMessages = maxUncompressedMessages; }
    }

    // ========== 外层 getters/setters ==========
    public Compression getCompression() { return compression; }
    public void setCompression(Compression compression) { this.compression = compression; }
}
