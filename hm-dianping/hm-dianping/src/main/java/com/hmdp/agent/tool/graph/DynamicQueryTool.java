package com.hmdp.agent.tool.graph;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 动态查询工具 — 根据 LLM 生成的 JSON 参数灵活查询商家。
 * 白名单字段防注入 + SQL 注入防护。
 */
@Component
public class DynamicQueryTool {

    private static final Logger log = LoggerFactory.getLogger(DynamicQueryTool.class);

    // 允许查询的字段白名单
    private static final Set<String> ALLOWED_FIELDS = new HashSet<>(Arrays.asList(
            "id", "name", "type_id", "score", "avg_price", "sold", "comments", "area", "address"
    ));
    private static final Set<String> ALLOWED_SORT = new HashSet<>(Arrays.asList(
            "score", "avg_price", "sold", "comments", "id"
    ));

    @Resource
    private ShopMapper shopMapper;

    /**
     * @param argsJson LLM 生成的 JSON，如 {"filters":{"type_id":1,"name":"烧烤"},"sort":"score","order":"desc","limit":5}
     */
    public String query(String argsJson) {
        try {
            Map<String, Object> args = JSONUtil.parseObj(argsJson);
            QueryWrapper<Shop> wrapper = new QueryWrapper<>();

            // 解析 filters
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) args.getOrDefault("filters", new HashMap<>());
            for (Map.Entry<String, Object> e : filters.entrySet()) {
                if (!ALLOWED_FIELDS.contains(e.getKey())) {
                    log.warn("Blocked field: {}", e.getKey());
                    continue;
                }
                // 名称模糊匹配
                if ("name".equals(e.getKey())) {
                    wrapper.like("name", e.getValue().toString());
                } else {
                    wrapper.eq(e.getKey(), e.getValue());
                }
            }

            // 解析排序
            String sort = (String) args.getOrDefault("sort", "score");
            if (!ALLOWED_SORT.contains(sort)) sort = "score";
            String order = "desc".equalsIgnoreCase((String) args.get("order")) ? "desc" : "asc";
            wrapper.orderByDesc(sort);

            // 解析 limit
            int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 10;
            if (limit > 20) limit = 20;
            wrapper.last("LIMIT " + limit);

            List<Shop> shops = shopMapper.selectList(wrapper);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Shop s : shops) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", s.getId());
                item.put("name", s.getName());
                item.put("score", s.getScore());
                item.put("avgPrice", s.getAvgPrice());
                item.put("sold", s.getSold());
                item.put("comments", s.getComments());
                item.put("area", s.getArea());
                item.put("address", s.getAddress());
                result.add(item);
            }
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("DynamicQuery failed", e);
            return "查询失败: " + e.getMessage();
        }
    }
}
