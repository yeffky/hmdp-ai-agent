package com.hmdp.agent.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SkillRegistry：加载 agent-skills 目录 + 按意图触发词匹配 + 工具能力边界。
 */
class SkillRegistryTest {

    private SkillRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new SkillRegistry();
        registry.init();
    }

    @Test
    void loadsAllSkills() {
        Set<String> shop = registry.toolNames(registry.matchSkills("附近有什么火锅店"));
        assertTrue(shop.contains("geoSearch"));
        assertTrue(shop.contains("searchShop"));
        assertTrue(shop.contains("searchShops"));
        assertTrue(shop.contains("recommendShops"));
        assertTrue(shop.contains("listShopVouchers"));

        Set<String> order = registry.toolNames(registry.matchSkills("查看我的订单"));
        assertTrue(order.contains("queryMyOrders"));

        Set<String> queue = registry.toolNames(registry.matchSkills("帮我排队取号"));
        assertTrue(queue.contains("takeQueueNumber"));
        assertTrue(queue.contains("queryMyQueueStatus"));
        assertTrue(queue.contains("cancelMyQueue"));

        Set<String> content = registry.toolNames(registry.matchSkills("这家店的探店笔记"));
        assertTrue(content.contains("queryShopBlogs"));
        assertTrue(content.contains("queryShopComments"));

        Set<String> knowledge = registry.toolNames(registry.matchSkills("怎么退款"));
        assertTrue(knowledge.contains("searchKnowledge"));

        Set<String> history = registry.toolNames(registry.matchSkills("上次我们聊过的"));
        assertTrue(history.contains("searchHistory"));
    }

    /** skill 能力边界：每个 skill 自身工具列表只含本技能工具，不越权标注其他 skill 的方法。
     *  直接查 skill.tools()（不经 matchSkills 合并——触发词可能命中多个 skill 属正常依赖）。 */
    @Test
    void skill边界清晰_不越权标注其他技能工具() {
        // shop：不含评价/笔记工具（属 content）
        assertFalse(new HashSet<>(registry.get("shop").tools()).contains("queryShopComments"), "shop 不应含评价工具（content）");
        assertFalse(new HashSet<>(registry.get("shop").tools()).contains("queryShopBlogs"), "shop 不应含笔记工具（content）");
        // queue：不含店铺搜索工具（属 shop）
        assertFalse(new HashSet<>(registry.get("queue").tools()).contains("searchShop"), "queue 不应含店铺搜索工具（shop）");
        assertFalse(new HashSet<>(registry.get("queue").tools()).contains("searchShops"), "queue 不应含店铺搜索工具（shop）");
        // content：不含店铺搜索工具（属 shop）
        assertFalse(new HashSet<>(registry.get("content").tools()).contains("searchShop"), "content 不应含店铺搜索工具（shop）");
        assertFalse(new HashSet<>(registry.get("content").tools()).contains("searchShops"), "content 不应含店铺搜索工具（shop）");
        // general：只含 text2sql，不含店铺确定性工具
        assertFalse(new HashSet<>(registry.get("general").tools()).contains("searchShops"), "general 不应含店铺工具");
    }

    @Test
    void unmatchedFallsBackToGeneral() {
        List<Skill> matched = registry.matchSkills("今天天气怎么样");
        // 无匹配 skill → 只有 general 兜底
        assertTrue(matched.stream().allMatch(s -> "general".equals(s.name())));
        Set<String> names = registry.toolNames(matched);
        assertTrue(names.contains("query"));
        // general 兜底只含 text2sql，不含店铺确定性工具（属 shop/content）
        assertFalse(names.contains("searchShops"));
        assertFalse(names.contains("queryShopComments"));
    }

    @Test
    void extractAnswerRules_onlyKeepsAnswerSection() {
        String rules = "## 何时使用\n...\n## 工具决策\n...\n## 回答规则（Answer 阶段）\n- 涉及店铺必须输出 [[id]]\n- 占位符紧跟店名\n## 护栏\n...";
        String extracted = SkillRegistry.extractAnswerRules(rules);
        assertTrue(extracted.contains("[[id]]"), extracted);
        assertTrue(extracted.contains("占位符紧跟店名"), extracted);
        assertFalse(extracted.contains("工具决策"), "不应包含决策阶段段落");
        assertFalse(extracted.contains("护栏"), "不应包含护栏段落");
    }

    @Test
    void extractAnswerRules_noSection_returnsEmpty() {
        assertTrue(SkillRegistry.extractAnswerRules("## 何时使用\n## 工具决策").isEmpty());
        assertTrue(SkillRegistry.extractAnswerRules("").isEmpty());
        assertTrue(SkillRegistry.extractAnswerRules(null).isEmpty());
    }

    @Test
    void answerRulesText_injectsAnswerSectionAndAnswerRefs_only() {
        // shop：回答规则段注入；answer-* references 已删除（[[id]] 协议废弃，卡片由 showCards 声明渲染）；
        // food-categories（SOP）不注入
        List<Skill> matched = registry.matchSkills("附近有什么火锅店");
        String text = registry.answerRulesText(matched);
        assertTrue(text.contains("回答规则"), "应含回答规则段");
        assertTrue(text.contains("店名"), "应含卡片展示说明");
        assertFalse(text.contains("food-categories"), "SOP 类 references 不应注入 Answer");
        assertFalse(text.contains("工具决策"), "决策阶段段落不应注入 Answer");
        assertFalse(text.contains("answer-card-examples"), "answer- 前缀 references 已废弃不应注入");
    }

    @Test
    void decisionRulesText_excludesAnswerSectionAndAnswerRefs() {
        // 决策阶段：SKILL.md 剔除「回答规则」段（Answer 专属），references 剔除 answer- 前缀
        List<Skill> matched = registry.matchSkills("附近有什么火锅店");
        String rules = registry.decisionRulesText(matched);
        assertTrue(rules.contains("工具决策"), "应保留决策阶段段落");
        assertTrue(rules.contains("流程"), "应保留流程段落");
        assertFalse(rules.contains("回答规则"), "回答规则段不应注入决策阶段");
        assertFalse(rules.contains("不得因上下文历史回答没有卡片而省略"), "回答规则段特有措辞不应注入决策阶段");
        String refs = registry.referencesText(matched);
        assertTrue(refs.contains("food-categories"), "SOP 类 references 应注入决策阶段");
        assertFalse(refs.contains("answer-card-examples"), "answer- 前缀 references 不应注入决策阶段");
    }
}
