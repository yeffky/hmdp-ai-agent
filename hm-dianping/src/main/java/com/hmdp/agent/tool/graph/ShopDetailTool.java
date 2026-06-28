package com.hmdp.agent.tool.graph;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商家详情工具 — 根据 ID 查询商家完整信息。
 */
@Component
public class ShopDetailTool {

    private static final Logger log = LoggerFactory.getLogger(ShopDetailTool.class);

    @Resource
    private ShopMapper shopMapper;

    @Tool("查询指定商家的详细信息，包括名称、评分、均价、地址、营业时间等")
    public String getDetail(@P("商家ID") long shopId) {
        try {
            Shop shop = shopMapper.selectById(shopId);
            if (shop == null) return "商家不存在 (id=" + shopId + ")";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", shop.getId());
            item.put("name", shop.getName());
            item.put("score", shop.getScore());
            item.put("avgPrice", shop.getAvgPrice());
            item.put("sold", shop.getSold());
            item.put("comments", shop.getComments());
            item.put("area", shop.getArea());
            item.put("address", shop.getAddress());
            item.put("openHours", shop.getOpenHours());
            if (shop.getX() != null) item.put("x", shop.getX());
            if (shop.getY() != null) item.put("y", shop.getY());
            return JSONUtil.toJsonPrettyStr(item);
        } catch (Exception e) {
            log.error("ShopDetail failed", e);
            return "查询失败: " + e.getMessage();
        }
    }
}
