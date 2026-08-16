package com.hmdp.agent.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.agent.ToolContext;
import com.hmdp.agent.graph.error.ToolException;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.utils.IdObfuscator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性店铺/团购/推荐工具 — 替代开放式的 text2Sql，覆盖「搜店筛选」「团购查询」「结构化推荐」。
 *
 * <p>不再让 LLM 生成 SQL：所有条件通过参数化工具执行，确定性强、无 SQL 注入/语法风险。
 * <p>备注：score 存储为 INT（5分×10），scoreMin 按 0-5 传入，内部换算。
 */
@Component
public class ShopQueryTool {

    private static final Logger log = LoggerFactory.getLogger(ShopQueryTool.class);

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private IdObfuscator idObfuscator;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @PostConstruct
    void init() {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Tool("按条件筛选店铺，支持组合过滤与排序。适用于：按美食细分/评分/人均/停车/儿童/宠物/地区筛选店铺、排行榜（评分最高/人气最高/销量最高）等确定性查询。返回统一商家卡列表：每家含推荐理由（★评分最高/人均最实惠/人气最高/综合推荐）+ 基础信息（距离/宠物友好/招牌菜/评价总结），前端按此渲染卡片，回答文本不必复述。所有参数都可选：只传用户明确提到的条件，未提到的参数不要传（禁止填 0/空串/false 占位，那会被视为未提供）。")
    public String searchShops(
            @P("商家类型ID（可选）：1美食/2KTV/3美发/4健身/5按摩/6SPA/7亲子/8酒吧/9轰趴/10美甲") Integer typeId,
            @P("美食细分（可选，仅美食类，用户说的具体菜系映射到此处）：奶茶咖啡/快餐小吃/火锅/烧烤烤肉/地方菜系/异域料理/自助餐/海鲜/面包蛋糕/食品生鲜") String foodCategory,
            @P("地区ID（可选，默认不限定）：1拱墅区/2鼓楼区") Long districtId,
            @P("店铺名关键词（可选，模糊匹配）") String name,
            @P("最低评分（可选，0-5，如 4.5 表示评分≥4.5）") Double scoreMin,
            @P("人均价格下限（可选，元）") Integer priceMin,
            @P("人均价格上限（可选，元）") Integer priceMax,
            @P("是否支持停车（可选）") Boolean hasParking,
            @P("是否儿童友好（可选）") Boolean childFriendly,
            @P("是否宠物友好（可选）") Boolean petFriendly,
            @P("排序（可选）：score评分/comments人气/sold销量/price价格") String sortBy,
            @P("返回条数（可选，默认10，最大20）") Integer limit) {

        QueryWrapper<Shop> qw = new QueryWrapper<>();
        // 防御 LLM 填充默认值：0 / 空串 / false 一律视为"未提供"，避免 0 值条件把结果筛空
        if (typeId != null && typeId > 0) qw.eq("type_id", typeId);
        if (foodCategory != null && !foodCategory.isBlank()) qw.eq("food_category", foodCategory);
        // 未指定地区时默认按用户当前地区过滤（避免跨地区推荐，如定位福州却推杭州）
        Long effectiveDistrict = (districtId != null && districtId > 0)
                ? districtId : com.hmdp.agent.ToolContext.getDistrictId();
        if (effectiveDistrict != null && effectiveDistrict > 0) qw.eq("district_id", effectiveDistrict);
        if (name != null && !name.isBlank()) qw.like("name", name);
        if (scoreMin != null && scoreMin > 0) qw.ge("score", (int) Math.round(scoreMin * 10));
        if (priceMin != null && priceMin > 0) qw.ge("avg_price", priceMin);
        if (priceMax != null && priceMax > 0) qw.le("avg_price", priceMax);
        if (Boolean.TRUE.equals(hasParking)) qw.eq("has_parking", 1);
        if (Boolean.TRUE.equals(childFriendly)) qw.eq("child_friendly", 1);
        if (Boolean.TRUE.equals(petFriendly)) qw.eq("pet_friendly", 1);
        if ("score".equals(sortBy)) qw.orderByDesc("score").orderByDesc("comments");
        else if ("comments".equals(sortBy)) qw.orderByDesc("comments");
        else if ("sold".equals(sortBy)) qw.orderByDesc("sold");
        else if ("price".equals(sortBy)) qw.orderByAsc("avg_price");
        else qw.orderByDesc("score").orderByDesc("comments");
        qw.last("LIMIT " + Math.min(limit == null ? 10 : limit, 20));

        try {
            List<Shop> shops = shopMapper.selectList(qw);
            if (shops.isEmpty()) {
                return "未找到满足条件的店铺。当前筛选条件: "
                        + describeFilters(typeId, foodCategory, districtId, name, scoreMin, priceMin, priceMax);
            }
            // 统一商家卡：搜索/筛选/排行返回的每一家都附推荐理由 + 基础信息（距离/宠物友好/招牌菜/评价总结），供前端卡片结构化渲染
            Map<Long, Map<String, Object>> reviewMap = buildReviewMap(shops);
            List<Map<String, Object>> recs = new ArrayList<>();
            for (Shop s : shops) recs.add(buildRecItem(s, reviewMap));
            assignReasons(recs);
            return JSONUtil.toJsonPrettyStr(recs);
        } catch (Exception e) {
            log.error("searchShops failed", e);
            throw new ToolException("searchShops", "店铺筛选失败: " + e.getMessage(), e);
        }
    }

    @Tool("查询指定店铺的团购套餐列表。需提供店铺ID（通常先由 searchShops/searchShop 获得）。返回套餐ID、标题、价格、是否秒杀。")
    public String listShopVouchers(@P("店铺ID（对外混淆ID）") String shopId) {
        Long realShopId = idObfuscator.decodeOrId(shopId);
        if (realShopId == null) {
            return "缺少店铺ID，请先搜索店铺获得ID。";
        }
        try {
            List<Voucher> vouchers = voucherMapper.selectList(
                    new QueryWrapper<Voucher>().eq("shop_id", realShopId).eq("status", 1));
            if (vouchers.isEmpty()) {
                return "该店铺暂无团购套餐。";
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (Voucher v : vouchers) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", idObfuscator.encode(v.getId()));
                item.put("title", v.getTitle());
                item.put("subTitle", v.getSubTitle());
                item.put("payValue", v.getPayValue());
                item.put("actualValue", v.getActualValue());
                item.put("type", v.getType()); // 0普通 / 1秒杀
                list.add(item);
            }
            return JSONUtil.toJsonPrettyStr(list);
        } catch (Exception e) {
            log.error("listShopVouchers failed for shop {}", shopId, e);
            throw new ToolException("listShopVouchers", "团购查询失败: " + e.getMessage(), e);
        }
    }

    @Tool("推荐店铺：返回结构化推荐列表，每家含推荐理由 + 基础信息（距离/宠物友好/招牌菜/评价总结）。适用于用户要「推荐/推荐几家/哪家好/评分高/性价比」等推荐场景。返回 JSON 结构固定，前端按此渲染卡片。")
    public String recommendShops(
            @P("美食细分（可选）：奶茶咖啡/快餐小吃/火锅/烧烤烤肉/地方菜系/异域料理/自助餐/海鲜/面包蛋糕/食品生鲜") String foodCategory,
            @P("商家类型ID（可选）：1美食/2KTV/3美发/4健身/5按摩/6SPA/7亲子/8酒吧/9轰趴/10美甲") Integer typeId,
            @P("是否宠物友好（可选）") Boolean petFriendly,
            @P("返回条数（可选，默认3，最大5）") Integer limit) {
        QueryWrapper<Shop> qw = new QueryWrapper<>();
        if (typeId != null && typeId > 0) qw.eq("type_id", typeId);
        else qw.eq("type_id", 1);
        if (foodCategory != null && !foodCategory.isBlank()) qw.eq("food_category", foodCategory);
        Long effectiveDistrict = ToolContext.getDistrictId();
        if (effectiveDistrict != null && effectiveDistrict > 0) qw.eq("district_id", effectiveDistrict);
        if (Boolean.TRUE.equals(petFriendly)) qw.eq("pet_friendly", 1);
        qw.orderByDesc("score").orderByDesc("comments");
        qw.last("LIMIT " + Math.min(limit == null ? 3 : limit, 5));

        try {
            List<Shop> shops = shopMapper.selectList(qw);
            if (shops.isEmpty()) return "未找到满足条件的店铺，请尝试放宽条件。";
            Map<Long, Map<String, Object>> reviewMap = buildReviewMap(shops);
            List<Map<String, Object>> recs = new ArrayList<>();
            for (Shop s : shops) recs.add(buildRecItem(s, reviewMap));
            assignReasons(recs);
            return JSONUtil.toJsonPrettyStr(recs);
        } catch (Exception e) {
            log.error("recommendShops failed", e);
            throw new ToolException("recommendShops", "推荐失败: " + e.getMessage(), e);
        }
    }

    /** 单店推荐项：严格 schema（推荐理由 + 基础信息），searchShops / recommendShops 共用。 */
    private Map<String, Object> buildRecItem(Shop s, Map<Long, Map<String, Object>> reviewMap) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reason", "");
        item.put("id", idObfuscator.encode(s.getId()));
        item.put("name", s.getName());
        String images = s.getImages();
        item.put("image", images != null && !images.isBlank() ? images.split(",")[0] : "");
        item.put("score", s.getScore());
        item.put("avgPrice", s.getAvgPrice());
        item.put("comments", s.getComments());
        item.put("foodCategory", s.getFoodCategory());
        item.put("area", s.getArea());
        item.put("address", s.getAddress());
        item.put("openHours", s.getOpenHours());
        item.put("distance", distanceToUser(s));
        item.put("petFriendly", Integer.valueOf(1).equals(s.getPetFriendly()));
        item.put("childFriendly", Integer.valueOf(1).equals(s.getChildFriendly()));
        item.put("hasParking", Integer.valueOf(1).equals(s.getHasParking()));
        item.put("signatureDishes", extractDishes(s.getDescription()));
        Map<String, Object> rv = reviewMap.get(s.getId());
        item.put("reviewRating", rv != null ? rv.get("avgRating") : null);
        item.put("reviewCount", rv != null ? rv.get("count") : null);
        item.put("reviewSummary", rv != null && rv.get("topComment") != null ? rv.get("topComment") : "");
        return item;
    }

    /** 批查店铺评价：平均分 + 条数 + 一条高赞评论。 */
    private Map<Long, Map<String, Object>> buildReviewMap(List<Shop> shops) {
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        if (shops.isEmpty()) return map;
        StringBuilder in = new StringBuilder();
        List<Object> ids = new ArrayList<>();
        for (Shop s : shops) { in.append(in.length() == 0 ? "?" : ",?"); ids.add(s.getId()); }
        try {
            List<Map<String, Object>> agg = jdbc.queryForList(
                    "SELECT shop_id, ROUND(AVG(rating),1) avg_r, COUNT(*) cnt FROM tb_shop_comment WHERE shop_id IN (" + in + ") AND status=0 GROUP BY shop_id", ids.toArray());
            for (Map<String, Object> r : agg) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("avgRating", r.get("avg_r"));
                m.put("count", r.get("cnt"));
                map.put(((Number) r.get("shop_id")).longValue(), m);
            }
            List<Map<String, Object>> top = jdbc.queryForList(
                    "SELECT shop_id, content FROM tb_shop_comment WHERE shop_id IN (" + in + ") AND status=0 ORDER BY liked DESC LIMIT 60", ids.toArray());
            for (Map<String, Object> r : top) {
                Long sid = ((Number) r.get("shop_id")).longValue();
                Map<String, Object> m = map.get(sid);
                if (m != null && m.get("topComment") == null) {
                    String c = String.valueOf(r.get("content"));
                    if (c.length() > 40) c = c.substring(0, 40) + "...";
                    m.put("topComment", c);
                }
            }
        } catch (Exception e) {
            log.warn("buildReviewMap failed: {}", e.getMessage());
        }
        return map;
    }

    /** 从描述「主打X，Y。」提取招牌菜。 */
    private static String extractDishes(String desc) {
        if (desc == null || desc.isBlank()) return "招牌菜品";
        try {
            int i = desc.indexOf("主打");
            int j = desc.indexOf("，", i + 2);
            if (i >= 0 && j > i) return desc.substring(i + 2, j);
        } catch (Exception ignored) {
        }
        return "招牌菜品";
    }

    /** 到用户定位的距离（米）。 */
    private static Long distanceToUser(Shop s) {
        Double x = ToolContext.getLocationX();
        Double y = ToolContext.getLocationY();
        if (x == null || y == null || s.getX() == null || s.getY() == null) return null;
        return Math.round(haversine(x, y, s.getX(), s.getY()));
    }

    private static double haversine(double lon1, double lat1, double lon2, double lat2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 分配推荐理由：评分最高 / 人均最实惠 / 人气最高 / 综合推荐（每家一个，不重复）。基于结果集真实比较，与排序位置无关；单条结果标综合推荐。 */
    private static void assignReasons(List<Map<String, Object>> recs) {
        if (recs.isEmpty()) return;
        if (recs.size() == 1) { recs.get(0).put("reason", "综合推荐"); return; }
        Map<String, Object> best = null;
        for (Map<String, Object> r : recs) {
            if (!hasReason(r.get("reason"))
                    && (best == null || num(r.get("score")) > num(best.get("score")))) best = r;
        }
        if (best != null) best.put("reason", "评分最高（" + fmtScore(best.get("score")) + "）");
        Map<String, Object> cheapest = null;
        for (Map<String, Object> r : recs) {
            if (!hasReason(r.get("reason"))
                    && (cheapest == null || num(r.get("avgPrice")) < num(cheapest.get("avgPrice")))) cheapest = r;
        }
        if (cheapest != null) cheapest.put("reason", "人均最实惠（¥" + cheapest.get("avgPrice") + "）");
        Map<String, Object> hottest = null;
        for (Map<String, Object> r : recs) {
            if (!hasReason(r.get("reason"))
                    && (hottest == null || num(r.get("comments")) > num(hottest.get("comments")))) hottest = r;
        }
        if (hottest != null) hottest.put("reason", "人气最高（" + hottest.get("comments") + "条）");
        for (Map<String, Object> r : recs) if (!hasReason(r.get("reason"))) r.put("reason", "综合推荐");
    }

    /** 推荐理由是否已赋值（空串/ null 视为未赋值）。 */
    private static boolean hasReason(Object reason) {
        return reason != null && !reason.toString().isBlank();
    }

    private static String fmtScore(Object s) {
        double v = s instanceof Number n ? n.doubleValue() / 10 : 0;
        return String.format("%.1f", v);
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private String describeFilters(Integer typeId, String foodCategory, Long districtId,
                                   String name, Double scoreMin, Integer priceMin, Integer priceMax) {
        StringBuilder sb = new StringBuilder();
        if (typeId != null) sb.append("类型=").append(typeId).append(" ");
        if (foodCategory != null) sb.append("细分=").append(foodCategory).append(" ");
        if (districtId != null) sb.append("地区=").append(districtId).append(" ");
        if (name != null) sb.append("名称含=").append(name).append(" ");
        if (scoreMin != null) sb.append("评分≥").append(scoreMin).append(" ");
        if (priceMin != null) sb.append("人均≥").append(priceMin).append(" ");
        if (priceMax != null) sb.append("人均≤").append(priceMax).append(" ");
        return sb.toString().trim();
    }
}
