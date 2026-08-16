package com.hmdp.controller;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 店铺卡片兜底补发的店名匹配逻辑（A2UI 协议校验思路的确定性护栏）：
 * 回答涉及店铺但未输出 [[id]] 时，按店名（全名/去括号主名）匹配工具结果补发卡片。
 * 卡片 id 为对外混淆 ID（Sqids 字符串），不再暴露数据库真实 ID。
 */
class ReactStreamControllerCardTest {

    private Map<String, Map<String, Object>> cards() {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        byId.put("xKq1aB", card("xKq1aB", "一家大饼(荷叶园店)"));
        byId.put("pLz9cD", card("pLz9cD", "牛约堡牛约汉堡(古运路店)"));
        byId.put("mVx3eF", card("mVx3eF", "杨妈锅贴豆腐坊"));
        return byId;
    }

    private Map<String, Object> card(String id, String name) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("name", name);
        return c;
    }

    @Test
    void matchesFullName() {
        List<Map<String, Object>> matched = ReactStreamController.matchCardsByAnswer(
                cards(), "为您推荐牛约堡牛约汉堡(古运路店)，评分4.6");
        assertEquals(1, matched.size());
        assertEquals("pLz9cD", matched.get(0).get("id"));
        // anchor = 命中的全名（前端据此在文本内定位）
        assertEquals("牛约堡牛约汉堡(古运路店)", matched.get(0).get("anchor"));
    }

    @Test
    void matchesCoreName_ignoresParentheticalSuffix() {
        // 回答只写主名（去括号）也能匹配，anchor = 主名
        List<Map<String, Object>> matched = ReactStreamController.matchCardsByAnswer(
                cards(), "为您推荐牛约堡牛约汉堡，评分4.6，人均84元");
        assertEquals(1, matched.size());
        assertEquals("pLz9cD", matched.get(0).get("id"));
        assertEquals("牛约堡牛约汉堡", matched.get(0).get("anchor"));
    }

    @Test
    void matchesMultipleShops() {
        List<Map<String, Object>> matched = ReactStreamController.matchCardsByAnswer(
                cards(), "为您推荐：一家大饼、牛约堡牛约汉堡、杨妈锅贴豆腐坊，都是快餐小吃");
        assertEquals(3, matched.size());
    }

    @Test
    void noShopMentioned_noMatch() {
        List<Map<String, Object>> matched = ReactStreamController.matchCardsByAnswer(
                cards(), "抱歉，在5公里范围内没有找到符合条件的店铺");
        assertTrue(matched.isEmpty());
    }

    @Test
    void emptyInputs_noMatch() {
        assertTrue(ReactStreamController.matchCardsByAnswer(cards(), "").isEmpty());
        assertTrue(ReactStreamController.matchCardsByAnswer(cards(), null).isEmpty());
        assertTrue(ReactStreamController.matchCardsByAnswer(null, "一家大饼").isEmpty());
    }

    @Test
    void findAnchor_fullNameAndCoreName() {
        // 全名命中
        assertEquals("牛约堡牛约汉堡(古运路店)",
                ReactStreamController.findAnchor("推荐牛约堡牛约汉堡(古运路店)，评分高", "牛约堡牛约汉堡(古运路店)"));
        // 去括号主名命中
        assertEquals("牛约堡牛约汉堡",
                ReactStreamController.findAnchor("推荐牛约堡牛约汉堡，评分高", "牛约堡牛约汉堡(古运路店)"));
        // 未命中 → null
        assertNull(ReactStreamController.findAnchor("推荐杨妈锅贴豆腐坊", "牛约堡牛约汉堡(古运路店)"));
        assertNull(ReactStreamController.findAnchor(null, "一家大饼"));
        assertNull(ReactStreamController.findAnchor("推荐", null));
    }

    @Test
    void resolveAnchor_waitsForMdCloser_whenNameAtTextEnd() {
        // 回归：`**店名` 结尾（chunk 尾部）时闭合 ** 未到，必须等，不能立即发（否则 before 含未闭合 ** → md 失效）
        assertNull(ReactStreamController.resolveAnchorForSend("推荐**牛约堡牛约汉堡(古运路店)", "牛约堡牛约汉堡(古运路店)"),
                "店名在文本末尾应等待闭合标记");
        // 无 md 包裹但店名也在末尾 → 同样等（后面可能有 **，也可能没有——流式结束由兜底补发）
        assertNull(ReactStreamController.resolveAnchorForSend("推荐牛约堡牛约汉堡(古运路店)", "牛约堡牛约汉堡(古运路店)"));
    }

    @Test
    void resolveAnchor_mdCloserComplete_returnsAnchorWithCloser() {
        // `**店名**` 完整：锚点含闭合标记，before 块将是完整加粗
        assertEquals("牛约堡牛约汉堡(古运路店)**",
                ReactStreamController.resolveAnchorForSend("推荐**牛约堡牛约汉堡(古运路店)**，评分高", "牛约堡牛约汉堡(古运路店)"));
        // 标记后已有内容（空格/标点）→ 到齐
        assertEquals("牛约堡牛约汉堡(古运路店)**",
                ReactStreamController.resolveAnchorForSend("1. **牛约堡牛约汉堡(古运路店)** 评分4.6", "牛约堡牛约汉堡(古运路店)"));
        // 斜体 _ 同样吸收
        assertEquals("一家大饼_",
                ReactStreamController.resolveAnchorForSend("_一家大饼_ 好评", "一家大饼"));
    }

    @Test
    void resolveAnchor_noMd_returnsPlainName() {
        assertEquals("牛约堡牛约汉堡(古运路店)",
                ReactStreamController.resolveAnchorForSend("推荐牛约堡牛约汉堡(古运路店)，评分高", "牛约堡牛约汉堡(古运路店)"));
    }

    @Test
    void resolveAnchor_coreNameOnly_returnsNull() {
        // 仅主名匹配（无法确定括号后缀与 md 边界）→ 交流式结束兜底
        assertNull(ReactStreamController.resolveAnchorForSend("推荐牛约堡牛约汉堡，评分高", "牛约堡牛约汉堡(古运路店)"));
        // 店名未出现 → null
        assertNull(ReactStreamController.resolveAnchorForSend("推荐杨妈锅贴豆腐坊", "牛约堡牛约汉堡(古运路店)"));
    }
}
