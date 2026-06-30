package com.hmdp.agent.tool.graph;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * Geo 搜索工具 — 按地理距离搜索商家。
 */
@Component
public class GeoSearchTool {

    private static final Logger log = LoggerFactory.getLogger(GeoSearchTool.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    @Tool("按地理位置搜索指定类型的商家，返回距离范围内的商家列表（含名称、评分、均价、距离等）")
    public String search(
            @P("商家类型ID（整数），如: 1=美食, 2=KTV, 3=酒店。不确定时先用 searchKnowledge 查询") int typeId,
            @P("用户当前经度") double x,
            @P("用户当前纬度") double y,
            @P("搜索半径（米），默认5000") int radius) {
        try {
            String key = SHOP_GEO_KEY + typeId;

            Long geoSize = stringRedisTemplate.opsForZSet().size(key);
            log.info("geo size: {}", geoSize);
            if (geoSize == null || geoSize == 0) {
                loadShopsToGeo(typeId, key);
            }

            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                    .radius(key,
                            new Circle(x, y, radius),
                            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                    .includeDistance().sortAscending().limit(20));
            log.info("results: {}", JSONUtil.toJsonStr(results));
            if (results == null) return "[]";

            List<Map<String, Object>> list = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
                Long shopId = Long.valueOf(r.getContent().getName());
                Shop shop = shopMapper.selectById(shopId);
                if (shop != null) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", shop.getId());
                    item.put("name", shop.getName());
                    item.put("score", shop.getScore());
                    item.put("avgPrice", shop.getAvgPrice());
                    item.put("area", shop.getArea());
                    item.put("address", shop.getAddress());
                    item.put("distance", r.getDistance().getValue());
                    list.add(item);
                }
            }
            return JSONUtil.toJsonPrettyStr(list);
        } catch (Exception e) {
            log.error("GeoSearch failed", e);
            return "Geo搜索失败: " + e.getMessage();
        }
    }

    private void loadShopsToGeo(Integer typeId, String geoKey) {
        List<Shop> shops = shopMapper.selectList(
                new QueryWrapper<Shop>().eq("type_id", typeId));
        for (Shop shop : shops) {
            if (shop.getX() != null && shop.getY() != null) {
                stringRedisTemplate.opsForGeo().add(geoKey,
                        new Point(shop.getX(), shop.getY()),
                        shop.getId().toString());
            }
        }
    }
}
