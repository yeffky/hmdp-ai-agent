package com.hmdp.agent.tool.graph;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.agent.ToolContext;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.agent.graph.error.ToolException;
import com.hmdp.agent.tool.ShopTypeProvider;
import com.hmdp.agent.tool.dto.GeoSearchResult;
import com.hmdp.agent.tool.dto.ShopHit;
import com.hmdp.agent.tool.param.ToolParamException;
import com.hmdp.agent.tool.param.ToolParams;
import com.hmdp.utils.IdObfuscator;
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

    @Resource
    private ShopTypeProvider shopTypeProvider;

    @Resource
    private IdObfuscator idObfuscator;

    @Tool("按地理位置搜索指定类型的商家。类型ID与名称的映射见 shop skill 的 references/shop-types.md（10 类：1美食/2KTV/3美发/4健身/5按摩/6SPA/7亲子/8酒吧/9轰趴桌游剧本杀/10美容）；用户说的具体菜系（如茶餐厅、火锅）应归类到美食（typeId=1），不要当作类型名去搜类型表。**不在类型表中的商家（电影院/密室/棋牌等）禁止使用本工具**——改用 searchShops(name=关键词) 按名称搜索。定位坐标由系统上下文注入，无需也不应传入坐标参数")
    public GeoSearchResult geoSearch(
            @P("商家类型ID（整数）。具体可选值与名称见 references/shop-types.md。用户说的火锅/茶餐厅/日料等都属于美食（1）") int typeId,
            @P("搜索半径（米，范围100~50000）") int radius) {
        ToolParams.typeId(typeId);
        if (!shopTypeProvider.isValidType(typeId)) {
            throw new ToolParamException("商家类型ID无效，当前可选类型: " + shopTypeProvider.typeText()
                    + "（当前为 " + typeId + "）");
        }
        // 定位坐标由 ToolContext 注入（系统上下文），不从 LLM 参数获取
        Double x = ToolContext.getLocationX();
        Double y = ToolContext.getLocationY();
        if (x == null || y == null) {
            throw new ToolParamException("未获取到用户定位坐标，无法按位置搜索商家");
        }
        ToolParams.lngLat(x, y);
        ToolParams.radius(radius);
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
            if (results == null) return new GeoSearchResult();

            List<ShopHit> shops = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
                Long shopId = Long.valueOf(r.getContent().getName());
                Shop shop = shopMapper.selectById(shopId);
                if (shop != null) {
                    ShopHit hit = new ShopHit();
                    hit.setId(idObfuscator.encode(shop.getId()));
                    hit.setName(shop.getName());
                    hit.setScore(shop.getScore());
                    hit.setAvgPrice(shop.getAvgPrice());
                    hit.setArea(shop.getArea());
                    hit.setAddress(shop.getAddress());
                    hit.setDistance(r.getDistance().getValue());
                    shops.add(hit);
                }
            }

            // Redis GEO 未命中 → MySQL 兜底：按 typeId 全量查，Haversine 过滤半径并按距离排序
            if (shops.isEmpty()) {
                log.info("geoSearch: Redis empty, falling back to MySQL for typeId={}", typeId);
                shops = fallbackSearchFromDb(typeId, x, y, radius);
            }

            GeoSearchResult res = new GeoSearchResult();
            res.setShops(shops);
            return res;
        } catch (Exception e) {
            log.error("GeoSearch failed", e);
            throw new ToolException("geoSearch", "Geo搜索失败: " + e.getMessage(), e);
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

    /** MySQL 兜底：按 typeId 全量查，Haversine 距离过滤 + 排序。 */
    private List<ShopHit> fallbackSearchFromDb(Integer typeId, double x, double y, double radius) {
        List<Shop> all = shopMapper.selectList(new QueryWrapper<Shop>().eq("type_id", typeId));
        List<ShopHit> hits = new ArrayList<>();
        for (Shop shop : all) {
            if (shop.getX() == null || shop.getY() == null) continue;
            double d = haversine(x, y, shop.getX(), shop.getY());
            if (d <= radius) {
                ShopHit hit = new ShopHit();
                hit.setId(idObfuscator.encode(shop.getId()));
                hit.setName(shop.getName());
                hit.setScore(shop.getScore());
                hit.setAvgPrice(shop.getAvgPrice());
                hit.setArea(shop.getArea());
                hit.setAddress(shop.getAddress());
                hit.setDistance(d);
                hits.add(hit);
            }
        }
        hits.sort(Comparator.comparingDouble(ShopHit::getDistance));
        return hits.size() > 20 ? hits.subList(0, 20) : hits;
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
}
