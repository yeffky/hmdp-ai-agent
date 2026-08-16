package com.hmdp.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 店铺类工具结果的 id 统一混淆（运行时）：
 *
 * <p>历史会话注入的 tool 结果（checkpoint/摘要里）可能仍是混淆改造前的数据库真实数字 id。
 * 在注入给 LLM 前（AnswerInjection 折叠）与收集卡片时（ReactStreamController.collectShopCards）
 * 用同一方法把数字 id 统一换成对外混淆串，保证：
 * <ul>
 *   <li>LLM 上下文里永远只看到混淆短串（不会再看到 456 这类数据库 id）；</li>
 *   <li>LLM 复制的占位符值与卡片收集 key 一致（都能匹配上）。</li>
 * </ul>
 * 字符串 id（已是混淆串）原样保留；非店铺类工具 / 解析失败原样返回。
 */
public final class ShopResultIdObfuscator {

    private ShopResultIdObfuscator() {
    }

    /** 店铺类工具名：结果结构为 JSON 数组，或 {"shops":[...]}。 */
    private static boolean isShopTool(String toolName) {
        return "searchShops".equals(toolName) || "searchShop".equals(toolName)
                || "recommendShops".equals(toolName) || "geoSearch".equals(toolName);
    }

    /**
     * 把店铺工具结果 JSON 中每个店铺对象的数字 id 字段混淆为对外短串；失败原样返回。
     *
     * @param toolName 工具名（searchShops/searchShop/recommendShops/geoSearch）
     * @param content  工具结果 JSON 文本
     * @param obf      IdObfuscator
     */
    public static String obfuscate(String toolName, String content, IdObfuscator obf) {
        if (content == null || content.isBlank() || obf == null || !isShopTool(toolName)) {
            return content;
        }
        try {
            if ("geoSearch".equals(toolName)) {
                JSONObject obj = JSONUtil.parseObj(content);
                if (obj.get("shops") instanceof JSONArray arr) {
                    obfuscateArray(arr, obf);
                    return obj.toString();
                }
                return content;
            }
            Object parsed = JSONUtil.parse(content);
            if (parsed instanceof JSONArray arr) {
                obfuscateArray(arr, obf);
                return arr.toString();
            }
        } catch (Exception ignored) {
            // 解析失败原样返回（不阻塞注入）
        }
        return content;
    }

    private static void obfuscateArray(JSONArray arr, IdObfuscator obf) {
        for (Object o : arr) {
            if (o instanceof JSONObject jo) {
                Object id = jo.get("id");
                if (id instanceof Number n) {
                    String enc = obf.encode(n.longValue());
                    if (enc != null) {
                        jo.set("id", enc);
                    }
                }
            }
        }
    }

    /**
     * 精简 + 混淆店铺工具结果（仅注入给 LLM 的折叠文本用，不影响前端卡片——卡片数据源是原始 messages）：
     * 每家店只保留 LLM 写回答需要的字段，去掉对回答无用的长字段（image URL、reviewRating/reviewCount 与
     * score/comments 重复），长文本字段截断。把单家店从 ~600 字符压到 ~250，5 轮历史可省 ~70%。
     */
    public static String compact(String toolName, String content, IdObfuscator obf) {
        if (content == null || content.isBlank() || obf == null || !isShopTool(toolName)) {
            return content;
        }
        try {
            if ("geoSearch".equals(toolName)) {
                JSONObject obj = JSONUtil.parseObj(content);
                if (obj.get("shops") instanceof JSONArray arr) {
                    JSONArray out = compactArray(arr, obf);
                    return new JSONObject().set("shops", out).toString();
                }
                return content;
            }
            Object parsed = JSONUtil.parse(content);
            if (parsed instanceof JSONArray arr) {
                return compactArray(arr, obf).toString();
            }
        } catch (Exception ignored) {
            // 解析失败原样返回（不阻塞注入）
        }
        return content;
    }

    private static JSONArray compactArray(JSONArray arr, IdObfuscator obf) {
        JSONArray out = new JSONArray();
        for (Object o : arr) {
            if (o instanceof JSONObject jo) {
                JSONObject item = new JSONObject(true);
                item.set("id", compactId(jo, obf));
                item.set("name", jo.getStr("name", ""));
                item.set("reason", jo.getStr("reason", ""));
                item.set("score", jo.get("score"));
                item.set("avgPrice", jo.get("avgPrice"));
                item.set("comments", jo.get("comments"));
                item.set("distance", jo.get("distance"));
                item.set("foodCategory", jo.getStr("foodCategory", ""));
                item.set("openHours", jo.getStr("openHours", ""));
                item.set("signatureDishes", truncate(jo.getStr("signatureDishes", ""), 15));
                item.set("reviewSummary", truncate(jo.getStr("reviewSummary", ""), 30));
                item.set("petFriendly", jo.getBool("petFriendly", false));
                item.set("childFriendly", jo.getBool("childFriendly", false));
                item.set("hasParking", jo.getBool("hasParking", false));
                item.set("area", jo.getStr("area", ""));
                item.set("address", truncate(jo.getStr("address", ""), 30));
                out.add(item);
            }
        }
        return out;
    }

    private static Object compactId(JSONObject jo, IdObfuscator obf) {
        Object id = jo.get("id");
        if (id instanceof Number n) {
            String enc = obf.encode(n.longValue());
            if (enc != null) return enc;
        }
        return id;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
