package com.hmdp.agent.tool.graph;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 动态查询工具 — 灵活组合条件查询商家列表。
 * 白名单字段防注入。
 */
@Component
public class DynamicQueryTool {

    private static final Logger log = LoggerFactory.getLogger(DynamicQueryTool.class);

    private static final Set<String> ALLOWED_FIELDS = new HashSet<>(Arrays.asList(
            "id", "name", "type_id", "score", "avg_price", "sold", "comments", "area", "address"
    ));
    private static final Set<String> ALLOWED_SORT = new HashSet<>(Arrays.asList(
            "score", "avg_price", "sold", "comments", "id"
    ));

    @Resource
    private ShopMapper shopMapper;

    @Tool("灵活查询商家列表，支持按名称模糊搜索、按类型/区域筛选、按评分/销量排序")
    public String query(
            @P("筛选条件 JSON，如 {\"type_id\":1,\"name\":\"烧烤\"}。name 为模糊匹配，其他为精确匹配") String filters,
            @P("排序字段: score/avg_price/sold/comments/id") String sort,
            @P("排序方向: asc/desc") String order,
            @P("返回数量上限，默认10") int limit) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> filtersMap = (filters != null && !filters.isEmpty() && !"{}".equals(filters))
                    ? JSONUtil.parseObj(filters) : new HashMap<>();
            QueryWrapper<Shop> wrapper = new QueryWrapper<>();

            for (Map.Entry<String, Object> e : filtersMap.entrySet()) {
                if (!ALLOWED_FIELDS.contains(e.getKey())) {
                    log.warn("Blocked field: {}", e.getKey());
                    continue;
                }
                if ("name".equals(e.getKey())) {
                    wrapper.like("name", e.getValue().toString());
                } else {
                    wrapper.eq(e.getKey(), e.getValue());
                }
            }

            String sortField = (sort != null && ALLOWED_SORT.contains(sort)) ? sort : "score";
            if ("asc".equalsIgnoreCase(order)) {
                wrapper.orderByAsc(sortField);
            } else {
                wrapper.orderByDesc(sortField);
            }

            int lim = limit > 0 && limit <= 20 ? limit : 10;
            wrapper.last("LIMIT " + lim);

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
