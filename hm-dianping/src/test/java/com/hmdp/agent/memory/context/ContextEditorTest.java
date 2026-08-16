package com.hmdp.agent.memory.context;

import com.hmdp.agent.config.MemoryProperties;
import com.hmdp.agent.graph.nodes.Transcript;
import com.hmdp.agent.graph.state.ReActAgentState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContextEditor 组合拳单测 — filter（孤儿清理）+ trim（轮次对齐）+ placeholder。
 * 纯内存，不依赖 Spring/DB。
 */
class ContextEditorTest {

    private static final String PLACEHOLDER = "[已清理]";

    // ==================== 轮次锚点 ====================

    @Test
    void nthRealUserIdx_从后往前数真实用户并跳过注入提醒() {
        List<Map<String, String>> list = new ArrayList<>(List.of(
                ReActAgentState.userMsg("第一轮问题"),
                ReActAgentState.aiMsg("第一轮回答"),
                ReActAgentState.userMsg("[结果为空] 换思路"),   // 注入提醒，不算真实 user
                ReActAgentState.userMsg("第二轮问题"),
                ReActAgentState.aiMsg("第二轮回答"),
                ReActAgentState.userMsg("第三轮问题")));
        assertEquals(5, Transcript.nthRealUserIdx(list, 1));  // 最近真实 user = index 5
        assertEquals(3, Transcript.nthRealUserIdx(list, 2));  // 倒数第 2 = index 3
        assertEquals(0, Transcript.nthRealUserIdx(list, 3));  // 倒数第 3 = index 0
        assertEquals(-1, Transcript.nthRealUserIdx(list, 4)); // 只有 3 个真实 user
    }

    // ==================== filter：孤儿清理 + 注入提醒 ====================

    @Test
    void filterMessages_清理孤儿tool与注入提醒_保留配对() {
        List<Map<String, String>> list = new ArrayList<>(List.of(
                ReActAgentState.userMsg("[结果为空] 换思路"),   // nudge → 删
                ReActAgentState.userMsg("推荐火锅"),            // 保留
                ReActAgentState.assistantToolMsg("[{\"id\":\"c1\",\"name\":\"searchShops\",\"arguments\":\"{}\"}]"),
                ReActAgentState.toolMsg("searchShops", "c1", "[{shop}]"),     // 配对 → 保留
                ReActAgentState.toolMsg("orphanTool", "c99", "[orphan]")      // 孤儿 → 删
        ));
        Transcript.filterMessages(list);
        assertEquals(3, list.size());
        assertTrue(list.stream().anyMatch(m -> "c1".equals(m.get("toolCallId"))));
        assertFalse(list.stream().anyMatch(m -> "c99".equals(m.get("toolCallId"))));
        assertFalse(list.stream().anyMatch(m -> "[结果为空] 换思路".equals(m.get("content"))));
    }

    @Test
    void filterMessages_清理孤儿assistantToolCalls保留配对() {
        List<Map<String, String>> list = new ArrayList<>(List.of(
                ReActAgentState.userMsg("推荐火锅"),
                ReActAgentState.assistantToolMsg("[{\"id\":\"c1\",\"name\":\"searchShops\",\"arguments\":\"{}\"}]"),
                ReActAgentState.toolMsg("searchShops", "c1", "[{shop}]"),               // 配对 → 保留
                ReActAgentState.assistantToolMsg("[{\"id\":\"c2\",\"name\":\"takeQueueNumber\",\"arguments\":\"{}\"}]"), // 孤儿 assistant（无 tool 结果）→ 删
                ReActAgentState.userMsg("确认取号")
        ));
        Transcript.filterMessages(list);
        // user / assistant(c1) / tool(c1) / user 保留；孤儿 assistant(c2) 删除
        assertEquals(4, list.size());
        assertTrue(list.stream().anyMatch(m -> "c1".equals(m.get("toolCallId"))));
        assertFalse(list.stream().anyMatch(m -> m.get("toolCalls") != null && m.get("toolCalls").contains("c2")));
    }

    // ==================== trim：trigger + keep-rounds + placeholder ====================

    /** 构造 6 轮（user + assistant tool_calls + tool + ai），触发 messages 阈值后应保留最近 keepRounds 轮。 */
    @Test
    void apply_触发后按轮次对齐保留最近几轮_旧tool结果占位() throws Exception {
        ContextEditor editor = new ContextEditor(configWith(3, 10, null, List.of()));
        List<Map<String, String>> list = buildRounds(6);

        editor.apply(list);

        // 旧轮（index<cutoff）user/ai 被删，assistant tool_calls 保留，tool 结果占位；
        // 最近 3 轮完整保留（含 tool 原文）
        assertEquals(18, list.size());
        // 最近轮（第 5 轮）完整
        assertTrue(list.stream().anyMatch(m -> "第5轮：请推荐火锅".equals(m.get("content"))));
        assertTrue(list.stream().anyMatch(m -> "res5".equals(m.get("content"))));
        // 第 3 轮起完整保留（keepRounds=3 的起点）
        assertTrue(list.stream().anyMatch(m -> "第3轮：请推荐火锅".equals(m.get("content"))));
        assertTrue(list.stream().anyMatch(m -> "res3".equals(m.get("content"))));
        // 第 0 轮 user 被删、其 tool 结果占位
        assertFalse(list.stream().anyMatch(m -> "第0轮：请推荐火锅".equals(m.get("content"))));
        assertTrue(list.stream().anyMatch(m -> PLACEHOLDER.equals(m.get("content"))));
        // 旧轮 assistant tool_calls 调用痕迹保留（防 tool 占位符成孤儿）
        long oldToolCalls = list.stream()
                .filter(m -> "assistant".equals(m.get("role")) && m.get("toolCalls") != null)
                .count();
        assertEquals(6, oldToolCalls); // 6 轮 assistant tool_calls 全保留
    }

    @Test
    void apply_未超阈值不裁剪() throws Exception {
        ContextEditor editor = new ContextEditor(configWith(3, 10000, null, List.of()));
        List<Map<String, String>> list = buildRounds(6); // 6 轮 24 条，token 远小于 10000
        editor.apply(list);
        assertEquals(24, list.size()); // 不触发，原样保留
    }

    @Test
    void apply_excludeTools白名单旧tool结果整条保留() throws Exception {
        ContextEditor editor = new ContextEditor(configWith(3, 10, null, List.of("searchShops")));
        List<Map<String, String>> list = buildRounds(6);
        editor.apply(list);
        // 白名单工具结果不被占位：res0/res1/res2 应保留原文
        assertTrue(list.stream().anyMatch(m -> "res0".equals(m.get("content"))));
        assertTrue(list.stream().anyMatch(m -> "res2".equals(m.get("content"))));
        assertTrue(list.stream().noneMatch(m -> PLACEHOLDER.equals(m.get("content"))));
    }

    // ==================== 辅助 ====================

    /** 构造 n 轮：每轮 [user, assistant(tool_calls), tool(searchShops), ai]，共 4n 条。 */
    private List<Map<String, String>> buildRounds(int n) {
        List<Map<String, String>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(ReActAgentState.userMsg("第" + i + "轮：请推荐火锅"));
            list.add(ReActAgentState.assistantToolMsg(
                    "[{\"id\":\"c" + i + "\",\"name\":\"searchShops\",\"arguments\":\"{}\"}]"));
            list.add(ReActAgentState.toolMsg("searchShops", "c" + i, "res" + i));
            list.add(ReActAgentState.aiMsg("第" + i + "轮回答"));
        }
        return list;
    }

    /** 构造配置门面：props 反射注入（ContextEditingConfig 为 Spring bean，纯单测不走容器）。 */
    private ContextEditingConfig configWith(int keepRounds, Integer tokens, Integer messages, List<String> exclude)
            throws Exception {
        MemoryProperties mp = new MemoryProperties();
        MemoryProperties.ContextEditing ce = mp.getContextEditing();
        ce.setKeepRounds(keepRounds);
        ce.setTrigger(new ArrayList<>(List.of(new MemoryProperties.ContextEditing.Trigger(tokens, messages))));
        ce.setExcludeTools(exclude);
        ContextEditingConfig cfg = new ContextEditingConfig();
        Field f = ContextEditingConfig.class.getDeclaredField("props");
        f.setAccessible(true);
        f.set(cfg, ce);
        return cfg;
    }
}
