package com.hmdp.agent.tool.graph;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.impl.ShopServiceImpl;
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

    /**
     * @param typeId 商家类型
     * @param x      经度
     * @param y      纬度
     * @param radius 搜索半径（米）
     */
    public String search(int typeId, double x, double y, int radius) {
        try {
            String key = SHOP_GEO_KEY + typeId;

            // 缓存穿透：geo 为空时从 MySQL 加载
            Long geoSize = stringRedisTemplate.opsForZSet().size(key);
            if (geoSize == null || geoSize == 0) {
                loadShopsToGeo(typeId, key);
            }

            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                    .radius(key,
                            new Circle(x, y, radius),
                            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                    .includeDistance().sortAscending().limit(20));

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
