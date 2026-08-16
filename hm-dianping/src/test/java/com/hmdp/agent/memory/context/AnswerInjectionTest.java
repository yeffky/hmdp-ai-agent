package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.skill.Skill;
import com.hmdp.agent.skill.SkillRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Answer 注入回归测试：修复「Answer 阶段与 Agent 决策阶段上下文不一致」——
 * 用户指代性确认（如「是的就是这家」）时，Answer 必须能看到上一轮的店名/推荐文本，
 * 否则会丢失指代来源、脑补错误实体（曾出现把「建德豆腐包」答成「肯德基」）。
 */
class AnswerInjectionTest {

    private AnswerInjection buildInjection(int keepRounds) {
        AnswerInjection injection = new AnswerInjection();
        ContextEditor contextEditor = mock(ContextEditor.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        CompressionConfig compressionConfig = mock(CompressionConfig.class);
        when(compressionConfig.getKeepRecentRounds()).thenReturn(keepRounds);
        when(skillRegistry.matchSkills(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(injection, "contextEditor", contextEditor);
        ReflectionTestUtils.setField(injection, "skillRegistry", skillRegistry);
        ReflectionTestUtils.setField(injection, "compressionConfig", compressionConfig);
        return injection;
    }

    private ReActAgentState multiRoundState() {
        // 场景复现：上一轮推荐「建德豆腐包」，本轮用户确认「是的就是这家」（指代上一轮店名）
        Map<String, Object> init = new LinkedHashMap<>();
        init.put("messages", List.of(
                ReActAgentState.userMsg("帮我推荐附近好吃的豆腐包店"),
                ReActAgentState.aiMsg("为您推荐建德豆腐包（建华电子商务产业园店），评分4.5，人均30元"),
                ReActAgentState.userMsg("是的就是这家")
        ));
        init.put("userQuery", "是的就是这家");
        init.put("streamingPrompt", "回答规则");
        return new ReActAgentState(init);
    }

    @Test
    void build_injectsPreviousRoundShopName() {
        AnswerInjection injection = buildInjection(5); // keepRecentRounds=5：上一轮应被注入
        List<ChatMessage> msgs = injection.build(multiRoundState());
        String all = msgs.stream().map(String::valueOf).collect(Collectors.joining("\n"));
        assertTrue(all.contains("建德豆腐包"),
                "Answer 阶段应注入上一轮的店名上下文（否则指代「这家」会丢失）");
    }

    @Test
    void build_keepRoundsOne_excludesPreviousRound() {
        // 旧行为对照组：keepRecentRounds=1 只注入最后一轮 → 不含上一轮店名
        AnswerInjection injection = buildInjection(1);
        List<ChatMessage> msgs = injection.build(multiRoundState());
        String all = msgs.stream().map(String::valueOf).collect(Collectors.joining("\n"));
        assertTrue(!all.contains("建德豆腐包"),
                "keepRounds=1 时上一轮内容不应注入（对照组，验证修复前的行为差异）");
    }

    @Test
    void build_nullCompressionConfig_fallsBackToThreeRounds() {
        // compressionConfig 缺失（异常/单测场景）→ 兜底 keepRounds=3，仍能看到上一轮
        AnswerInjection injection = new AnswerInjection();
        ContextEditor contextEditor = mock(ContextEditor.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        when(skillRegistry.matchSkills(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(injection, "contextEditor", contextEditor);
        ReflectionTestUtils.setField(injection, "skillRegistry", skillRegistry);
        List<ChatMessage> msgs = injection.build(multiRoundState());
        String all = msgs.stream().map(String::valueOf).collect(Collectors.joining("\n"));
        assertTrue(all.contains("建德豆腐包"));
    }

    @Test
    void build_foldsToolCallsIntoPlainText_neverLeaksToolStructure() {
        // 回归：Answer 阶段无工具定义，若消息里带 assistant(tool_calls)+tool 结果，
        // DeepSeek 会以 <tool_calls><invoke> XML 形式输出（真实事故：整段 XML 回给用户）。
        // 必须折叠为纯文本：不含 tool_calls/tool 结构，工具结果以「工具[xxx] 结果：」文本保留。
        AnswerInjection injection = buildInjection(5);
        Map<String, Object> init = new LinkedHashMap<>();
        init.put("messages", List.of(
                ReActAgentState.userMsg("推荐汉堡店"),
                ReActAgentState.assistantToolMsg(
                        "[{\"id\":\"c1\",\"name\":\"searchShops\",\"arguments\":\"{\\\"foodCategory\\\":\\\"快餐小吃\\\"}\"}]"),
                ReActAgentState.toolMsg("searchShops", "c1", "[{\"name\":\"肯德基\"}]"),
                ReActAgentState.aiMsg("为您推荐以下店铺：肯德基（浙江大学城市学院店）")
        ));
        init.put("userQuery", "推荐汉堡店");
        init.put("streamingPrompt", "回答规则");
        ReActAgentState state = new ReActAgentState(init);

        List<ChatMessage> msgs = injection.build(state);
        String all = msgs.stream().map(String::valueOf).collect(Collectors.joining("\n"));
        // ① 无任何 tool_calls / tool 结果结构消息（只有纯文本；折叠文本里的工具名是正常内容）
        assertTrue(msgs.stream().noneMatch(m -> m instanceof AiMessage ai && ai.hasToolExecutionRequests()),
                "不应有 assistant tool_calls 结构消息");
        assertTrue(msgs.stream().noneMatch(m -> m instanceof ToolExecutionResultMessage),
                "不应有 role=tool 结构消息");
        // ② 工具结果折叠为纯文本证据保留
        assertTrue(all.contains("工具[searchShops] 结果"), "tool 结果应折叠为纯文本");
        assertTrue(all.contains("肯德基"), "工具结果内容应保留");
    }

    @Test
    void build_injectsShopAnswerRules_fromSkillAnswerRules() {
        // 卡片展示约束归属 skill（对齐 A2UI/Claude UI elements 的领域规则化思路）：
        // Answer 阶段按需加载各 skill 的「回答原则」（answerRulesText），不注入工具决策/流程等 SOP。
        AnswerInjection injection = new AnswerInjection();
        ContextEditor contextEditor = mock(ContextEditor.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        CompressionConfig compressionConfig = mock(CompressionConfig.class);
        when(compressionConfig.getKeepRecentRounds()).thenReturn(5);
        when(skillRegistry.matchSkills(any())).thenReturn(List.of(
                new Skill("shop", "搜索推荐店铺。触发词：店,火锅", "## 规则", List.of("searchShops"))));
        when(skillRegistry.answerRulesText(anyList())).thenReturn(
                "## 回答规则（卡片展示约束）\n- 涉及店铺必须输出 [[id]] 占位符\n- 即使历史回答无卡片也必须输出");
        ReflectionTestUtils.setField(injection, "contextEditor", contextEditor);
        ReflectionTestUtils.setField(injection, "skillRegistry", skillRegistry);
        ReflectionTestUtils.setField(injection, "compressionConfig", compressionConfig);

        List<ChatMessage> msgs = injection.build(multiRoundState());
        String all = msgs.stream().map(String::valueOf).collect(Collectors.joining("\n"));
        assertTrue(all.contains("[[id]] 占位符"), "shop skill 的回答原则应注入 Answer 上下文");
        assertTrue(!all.contains("硬性要求"), "代码层不应再有硬编码卡片要求（规则归属 skill）");
        // Answer 不再注入全量 rulesText / referencesText（SOP 属决策阶段）
        verify(skillRegistry, never()).rulesText(anyList());
        verify(skillRegistry, never()).referencesText(anyList());
    }
}
