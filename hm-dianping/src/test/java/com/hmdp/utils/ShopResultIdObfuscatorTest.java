package com.hmdp.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 店铺工具结果精简 + 混淆测试：
 * - 数字 id → 对外短串；字符串 id 保留
 * - image 等长字段被剔除（LLM 回答不需要，前端卡片数据源是原始 messages）
 * - address/reviewSummary 截断
 * - 非店铺工具 / 解析失败原样返回
 */
class ShopResultIdObfuscatorTest {

    private final IdObfuscator obf = new IdObfuscator("test-salt-shop");

    private String shopResult() {
        return "[{\"id\":456,\"name\":\"鲜牛门牛肉汤(拱宸桥店)\",\"image\":\"https://store.is.autonavi.com/showpic/very-long-url-abcdefghijklmnopqrstuvwxyz0123456789\","
                + "\"score\":46,\"avgPrice\":84,\"comments\":9,\"distance\":760,\"foodCategory\":\"快餐小吃\","
                + "\"openHours\":\"10:30-21:00\",\"signatureDishes\":\"卤味小吃\",\"reviewSummary\":\"环境不错，服务也很热情，会再来～\","
                + "\"petFriendly\":true,\"childFriendly\":false,\"hasParking\":true,\"area\":\"上塘\","
                + "\"address\":\"宸麟路399号宸麟府某栋某层某室超长地址用于截断验证更多内容补充\",\"reason\":\"评分最高（4.6）\","
                + "\"reviewRating\":4.6,\"reviewCount\":9}]";
    }

    @Test
    void compact_obfuscatesAndDropsLongFields() {
        String out = ShopResultIdObfuscator.compact("searchShops", shopResult(), obf);
        JSONArray arr = JSONUtil.parseArray(out);
        assertEquals(1, arr.size());
        JSONObject item = arr.getJSONObject(0);
        // 数字 id → 混淆短串
        assertFalse(item.getStr("id").matches("\\d+"));
        assertEquals(obf.encode(456L), item.getStr("id"));
        // image 剔除
        assertFalse(out.contains("image"));
        assertFalse(out.contains("showpic"));
        // reviewRating/reviewCount 剔除（与 score/comments 重复）
        assertFalse(out.contains("reviewRating"));
        assertFalse(out.contains("reviewCount"));
        // 长字段截断
        assertTrue(item.getStr("address").endsWith("…"));
        assertTrue(item.getStr("address").length() <= 31);
        // 关键字段保留
        assertEquals("鲜牛门牛肉汤(拱宸桥店)", item.getStr("name"));
        assertEquals(46, item.getInt("score"));
        assertEquals(84, item.getInt("avgPrice"));
        assertTrue(item.getBool("petFriendly"));
        assertEquals("上塘", item.getStr("area"));
    }

    @Test
    void compact_keepsStringIdAsIs() {
        String withShortId = shopResult().replace("\"id\":456", "\"id\":\"qcJLbZ\"");
        String out = ShopResultIdObfuscator.compact("recommendShops", withShortId, obf);
        JSONArray arr = JSONUtil.parseArray(out);
        assertEquals("qcJLbZ", arr.getJSONObject(0).getStr("id"));
    }

    @Test
    void compact_geoSearchStructure() {
        String geo = "{\"shops\":[{\"id\":456,\"name\":\"店A\",\"score\":46,\"avgPrice\":84,\"area\":\"上塘\",\"address\":\"路1号\",\"distance\":100}]}";
        String out = ShopResultIdObfuscator.compact("geoSearch", geo, obf);
        JSONObject obj = JSONUtil.parseObj(out);
        JSONArray arr = obj.getJSONArray("shops");
        assertEquals(obf.encode(456L), arr.getJSONObject(0).getStr("id"));
    }

    @Test
    void compact_nonShopToolOrInvalid_unchanged() {
        String text = "SELECT 1";
        assertEquals(text, ShopResultIdObfuscator.compact("text2Sql", text, obf));
        assertEquals("", ShopResultIdObfuscator.compact("searchShops", "", obf));
        assertEquals(null, ShopResultIdObfuscator.compact("searchShops", null, obf));
        // 非法 JSON 原样
        String bad = "not json";
        assertEquals(bad, ShopResultIdObfuscator.compact("searchShops", bad, obf));
    }
}
