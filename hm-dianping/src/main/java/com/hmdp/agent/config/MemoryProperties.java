package com.hmdp.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆/上下文管理相关配置。
 * 对应 application.yaml 中 agent.memory.*
 */
@Component
@ConfigurationProperties(prefix = "agent.memory")
public class MemoryProperties {

    private Compression compression = new Compression();

    private ContextEditing contextEditing = new ContextEditing();

    // ========== 压缩配置 ==========
    public static class Compression {
        /** 是否启用压缩 */
        private boolean enabled = true;
        /** 总 token 超过此值触发压缩 */
        private int compressThreshold = 4000;
        /** 保留最近 N tokens 不压缩 */
        private int keepRecentTokens = 2000;
        /** 保留最近 N 轮（真实 user 边界，含 tool 轨迹）不压缩，更早轮次压缩——供 Planner/ReAct 跨轮复用已获取信息 */
        private int keepRecentRounds = 5;
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

        public int getKeepRecentRounds() { return keepRecentRounds; }
        public void setKeepRecentRounds(int keepRecentRounds) { this.keepRecentRounds = keepRecentRounds; }

        public int getMaxSummaryTokens() { return maxSummaryTokens; }
        public void setMaxSummaryTokens(int maxSummaryTokens) { this.maxSummaryTokens = maxSummaryTokens; }

        public int getMaxUncompressedMessages() { return maxUncompressedMessages; }
        public void setMaxUncompressedMessages(int maxUncompressedMessages) { this.maxUncompressedMessages = maxUncompressedMessages; }
    }

    // ========== 上下文编辑（注入层）配置 ==========
    // 对齐 LangChain contextEditing 组合拳（filter_messages / trim_messages / SummarizationMiddleware）
    public static class ContextEditing {
        /** 是否启用注入层上下文编辑（孤儿清理 + 轮次对齐裁剪） */
        private boolean enabled = true;
        /** 多条件触发阈值：任一条件满足（条内 tokens/messages AND、条间 OR）即触发裁剪 */
        private List<Trigger> trigger = new ArrayList<>(List.of(
                new Trigger(20000, 40),
                new Trigger(12000, 80)));
        /** 触发后全量保留最近 N 轮（含工具轨迹，start_on=human 轮次对齐） */
        private int keepRounds = 3;
        /** 白名单工具：即使被裁区域也整条保留其结果，不替换占位符 */
        private List<String> excludeTools = new ArrayList<>();
        /** 被裁工具结果的替换占位符 */
        private String placeholder = "[已清理]";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<Trigger> getTrigger() { return trigger; }
        public void setTrigger(List<Trigger> trigger) { this.trigger = trigger; }

        public int getKeepRounds() { return keepRounds; }
        public void setKeepRounds(int keepRounds) { this.keepRounds = keepRounds; }

        public List<String> getExcludeTools() { return excludeTools; }
        public void setExcludeTools(List<String> excludeTools) { this.excludeTools = excludeTools; }

        public String getPlaceholder() { return placeholder; }
        public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }

        /** 单条触发条件：tokens/messages 阈值（fraction 因 langchain4j 难取模型 context 长度，由固定 token 预算替代） */
        public static class Trigger {
            private Integer tokens;
            private Integer messages;

            public Trigger() {}

            public Trigger(Integer tokens, Integer messages) {
                this.tokens = tokens;
                this.messages = messages;
            }

            public Integer getTokens() { return tokens; }
            public void setTokens(Integer tokens) { this.tokens = tokens; }

            public Integer getMessages() { return messages; }
            public void setMessages(Integer messages) { this.messages = messages; }
        }
    }

    // ========== 外层 getters/setters ==========
    public Compression getCompression() { return compression; }
    public void setCompression(Compression compression) { this.compression = compression; }

    public ContextEditing getContextEditing() { return contextEditing; }
    public void setContextEditing(ContextEditing contextEditing) { this.contextEditing = contextEditing; }
}
