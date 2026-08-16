package com.hmdp.agent.memory.context;

import com.hmdp.agent.config.MemoryProperties;
import com.hmdp.agent.graph.nodes.Transcript;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ContextEditor — LangChain contextEditing 组合拳的 Java 实现（filter + trim 注入层）。
 *
 * <p>注入模型前对 messages 就地编辑（调用方传副本，不写回 checkpoint，存储层裁剪仍归 SlidingWindowManager）：
 * <ol>
 *   <li><b>filter</b>（对齐 filter_messages）：孤儿 tool 清理 + 剔除注入提醒，无条件执行；</li>
 *   <li><b>trigger</b>（对齐 SummarizationMiddleware/ClearToolUsesEdit）：多条件阈值，条内 tokens/messages AND、条间 OR，任一满足触发；</li>
 *   <li><b>trim</b>（对齐 trim_messages start_on="human"）：按轮次对齐保留最近 {@code keep-rounds} 轮完整（含工具轨迹），
 *       更早轮次的 user/assistant 纯文本丢弃（由 compressedSummary 摘要承载）、assistant tool_calls 保留调用痕迹、
 *       tool 结果替换为占位符（excludeTools 白名单工具整条保留）。</li>
 * </ol>
 */
@Component
public class ContextEditor {

    private final ContextEditingConfig config;

    public ContextEditor(ContextEditingConfig config) {
        this.config = config;
    }

    /** 编辑注入副本（就地修改传入列表）。 */
    public void apply(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return;
        // ① filter：孤儿清理 + 剔除注入提醒（无条件，借鉴 ClearToolUsesEdit.apply 第一步）
        Transcript.filterMessages(messages);
        if (!config.isEnabled()) return;
        // ② trigger 多条件：任一满足触发
        if (!shouldEdit(messages)) return;
        // ③ trim：按轮次对齐（start_on=human），保留最近 keepRounds 轮完整
        int cutoff = Transcript.nthRealUserIdx(messages, config.getKeepRounds());
        if (cutoff <= 0) return;
        trimOldRounds(messages, cutoff);
    }

    /** 多条件触发：条内 tokens/messages AND、条间 OR。fraction 由固定 token 预算替代（langchain4j 难取模型 context 长度）。 */
    private boolean shouldEdit(List<Map<String, String>> messages) {
        int totalTokens = TokenCounter.countFromMap(messages);
        for (MemoryProperties.ContextEditing.Trigger c : config.getTrigger()) {
            boolean ok = true;
            if (c.getTokens() != null && totalTokens < c.getTokens()) ok = false;
            if (c.getMessages() != null && messages.size() < c.getMessages()) ok = false;
            if (ok) return true;
        }
        return false;
    }

    /**
     * cutoff 之前（旧轮次）：
     * - user 纯文本 → 丢弃（由摘要承载）
     * - assistant 带 tool_calls → 保留（调用痕迹，参数在，防 tool 占位符成孤儿）
     * - assistant 纯文本（最终回答）→ 丢弃（由摘要承载）
     * - tool 结果 → placeholder（保留 toolName/toolCallId，excludeTools 白名单整条保留原文）
     */
    private void trimOldRounds(List<Map<String, String>> messages, int cutoff) {
        Set<String> excluded = new HashSet<>(config.getExcludeTools());
        for (int i = 0; i < cutoff; i++) {
            Map<String, String> m = messages.get(i);
            String role = m.get("role");
            if ("tool".equals(role)) {
                String toolName = m.get("toolName");
                if (toolName == null || !excluded.contains(toolName)) {
                    m.put("content", config.getPlaceholder());
                    m.remove("isError");
                }
            } else if ("user".equals(role)) {
                messages.set(i, null);
            } else if ("assistant".equals(role)) {
                String callsJson = m.get("toolCalls");
                if (callsJson == null || callsJson.isBlank()) {
                    messages.set(i, null); // 纯文本回答 → 丢弃
                }
            }
        }
        // 移除被标记丢弃的（倒序避免索引错乱）
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) == null) messages.remove(i);
        }
    }
}
