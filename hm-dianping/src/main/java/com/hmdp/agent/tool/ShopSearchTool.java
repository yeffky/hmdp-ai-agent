package com.hmdp.agent.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.agent.graph.error.ToolException;
import com.hmdp.utils.IdObfuscator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 商铺搜索工具 — 按名称搜索商铺，返回混淆 ID + 基本信息。
 * 当用户提到具体商铺名称时，应先用此工具查出商铺ID，再进行后续操作。
 * 返回的 id 为对外混淆 ID（Sqids），回传工具参数时由各工具自动还原。
 */
@Component
public class ShopSearchTool {

    private static final Logger log = LoggerFactory.getLogger(ShopSearchTool.class);

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private IdObfuscator idObfuscator;

    @Tool("按名称搜索商铺，返回匹配的商铺列表（含ID、名称、地址、评分、均价等）。当用户提到具体商铺名称时，必须在调用其他需要shopId的工具之前先用此工具查出商铺ID。默认按用户当前定位地区过滤。")
    public String searchShop(
            @P("商铺名称关键词，支持模糊匹配。例如用户说'羊老三'，传入'羊老三'即可") String name,
            @P("地区ID（可选）：1拱墅区/2鼓楼区。不传默认按用户当前定位地区过滤；用户明确要查其它地区时传对应ID") Long districtId) {
        if (name == null || name.isBlank()) {
            return "请提供商铺名称关键词。";
        }

        try {
            QueryWrapper<Shop> qw = new QueryWrapper<Shop>().like("name", name);
            // 未指定地区时默认按用户当前定位地区过滤（避免定位福州却搜到杭州的店）
            Long effectiveDistrict = (districtId != null && districtId > 0)
                    ? districtId : com.hmdp.agent.ToolContext.getDistrictId();
            if (effectiveDistrict != null && effectiveDistrict > 0) {
                qw.eq("district_id", effectiveDistrict);
            }
            qw.last("LIMIT 10");
            List<Shop> shops = shopMapper.selectList(qw);

            if (shops.isEmpty()) {
                return "未找到名称包含「" + name + "」的商铺。请尝试更简短的关键词，或告知用户该商铺可能不存在。";
            }

            List<Map<String, Object>> list = new ArrayList<>();
            for (Shop shop : shops) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", idObfuscator.encode(shop.getId()));
                item.put("name", shop.getName());
                item.put("area", shop.getArea());
                item.put("address", shop.getAddress());
                item.put("score", shop.getScore());
                item.put("avgPrice", shop.getAvgPrice());
                item.put("comments", shop.getComments());
                item.put("openHours", shop.getOpenHours());
                list.add(item);
            }

            log.info("searchShop '{}' found {} results", name, list.size());
            return JSONUtil.toJsonPrettyStr(list);
        } catch (Exception e) {
            log.error("searchShop failed for '{}'", name, e);
            throw new ToolException("searchShop", "商铺搜索失败: " + e.getMessage(), e);
        }
    }
}
