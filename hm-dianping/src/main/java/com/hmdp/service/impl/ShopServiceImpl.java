package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_MAP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_MAP_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_MAP_VERSION_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_VERSION_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class,
                this::getById, 20L, TimeUnit.SECONDS);
        return shop == null ? Result.fail("店铺不存在！") : Result.ok(shop);
    }

    @Override
    public boolean save(Shop shop) {
        boolean saved = super.save(shop);
        if (saved) {
            evictShopCaches(null, shop);
        }
        return saved;
    }

    @Override
    public boolean removeById(Serializable id) {
        Shop oldShop = getById(id);
        boolean removed = super.removeById(id);
        if (removed) {
            evictShopCaches(oldShop, null);
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        Shop oldShop = getById(id);
        if (!updateById(shop)) {
            return Result.fail("店铺不存在或更新失败");
        }
        Shop effectiveShop = shop;
        if (oldShop != null) {
            effectiveShop = new Shop();
            effectiveShop.setId(id);
            effectiveShop.setTypeId(shop.getTypeId() == null ? oldShop.getTypeId() : shop.getTypeId());
            effectiveShop.setDistrictId(shop.getDistrictId() == null ? oldShop.getDistrictId() : shop.getDistrictId());
        }
        evictShopCaches(oldShop, effectiveShop);
        return Result.ok();
    }

    @Override
    public Result foodCategories(Long districtId) {
        QueryWrapper<Shop> qw = new QueryWrapper<Shop>()
                .select("food_category as name, COUNT(*) as cnt")
                .eq("type_id", 1)
                .isNotNull("food_category")
                .groupBy("food_category")
                .orderByDesc("cnt");
        if (districtId != null) {
            qw.eq("district_id", districtId);
        }
        return Result.ok(baseMapper.selectMaps(qw));
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y,
                                  Long districtId, String sortBy, String foodCategory) {
        if (x == null || y == null) {
            String key = CACHE_SHOP_LIST_KEY + cacheVersion(CACHE_SHOP_LIST_VERSION_KEY) + ":" + typeId
                    + ":" + (districtId == null ? 0 : districtId)
                    + ":" + (sortBy == null ? "" : sortBy)
                    + ":" + (foodCategory == null ? "" : foodCategory)
                    + ":" + current;
            List<Shop> shops = cacheClient.queryListWithPassThrough(key, Shop.class,
                    () -> query()
                            .eq(districtId != null, "district_id", districtId)
                            .eq("type_id", typeId)
                            .eq(StrUtil.isNotBlank(foodCategory), "food_category", foodCategory)
                            .orderByDesc("comments".equals(sortBy), "comments")
                            .orderByDesc("score".equals(sortBy), "score")
                            .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE)).getRecords(),
                    CACHE_SHOP_LIST_TTL, TimeUnit.MINUTES);
            return Result.ok(shops);
        }

        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + (districtId == null ? 0 : districtId) + ":" + typeId;
        Long geoSize = stringRedisTemplate.opsForZSet().size(key);
        if (geoSize == null || geoSize == 0) {
            loadShopsToGeo(typeId, districtId, key);
        }

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key, new org.springframework.data.geo.Circle(x, y, 5000),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance().sortAscending().limit(end));
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId, result.getDistance());
        });
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query()
                .in("id", ids)
                .eq(StrUtil.isNotBlank(foodCategory), "food_category", foodCategory)
                .last("order by field(id," + idStr + ")")
                .list();
        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
        return Result.ok(shops);
    }

    @Override
    public Result queryShopByName(String name, Integer current, Integer typeId,
                                  Long districtId, String sortBy) {
        String key = CACHE_SHOP_LIST_KEY + cacheVersion(CACHE_SHOP_LIST_VERSION_KEY) + ":name:"
                + (name == null ? "" : name) + ":" + (typeId == null ? 0 : typeId)
                + ":" + (districtId == null ? 0 : districtId)
                + ":" + (sortBy == null ? "" : sortBy) + ":" + current;
        List<Shop> shops = cacheClient.queryListWithPassThrough(key, Shop.class,
                () -> query()
                        .eq(districtId != null, "district_id", districtId)
                        .eq(typeId != null && typeId > 0, "type_id", typeId)
                        .like(StrUtil.isNotBlank(name), "name", name)
                        .orderByDesc("comments".equals(sortBy), "comments")
                        .orderByDesc("score".equals(sortBy), "score")
                        .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE)).getRecords(),
                CACHE_SHOP_LIST_TTL, TimeUnit.MINUTES);
        return Result.ok(shops);
    }

    @Override
    public Result queryShopsForMap(Long districtId, Integer typeId) {
        String key = CACHE_MAP_KEY + cacheVersion(CACHE_MAP_VERSION_KEY) + ":"
                + (districtId == null ? 0 : districtId) + ":" + (typeId == null ? 0 : typeId);
        List<Shop> shops = cacheClient.queryListWithPassThrough(key, Shop.class,
                () -> query()
                        .eq(districtId != null, "district_id", districtId)
                        .eq(typeId != null && typeId > 0, "type_id", typeId)
                        .list(),
                CACHE_MAP_TTL, TimeUnit.MINUTES);
        return Result.ok(shops);
    }

    private void evictShopCaches(Shop oldShop, Shop newShop) {
        if (oldShop != null && oldShop.getId() != null) {
            stringRedisTemplate.delete(CACHE_SHOP_KEY + oldShop.getId());
        }
        if (newShop != null && newShop.getId() != null) {
            stringRedisTemplate.delete(CACHE_SHOP_KEY + newShop.getId());
        }
        stringRedisTemplate.opsForValue().increment(CACHE_SHOP_LIST_VERSION_KEY);
        stringRedisTemplate.opsForValue().increment(CACHE_MAP_VERSION_KEY);
        deleteGeoCache(oldShop);
        deleteGeoCache(newShop);
    }

    private void deleteGeoCache(Shop shop) {
        if (shop != null && shop.getTypeId() != null) {
            stringRedisTemplate.delete(SHOP_GEO_KEY + (shop.getDistrictId() == null ? 0 : shop.getDistrictId())
                    + ":" + shop.getTypeId());
        }
    }

    private void loadShopsToGeo(Integer typeId, Long districtId, String geoKey) {
        List<Shop> shops = query().eq(districtId != null, "district_id", districtId)
                .eq("type_id", typeId).list();
        if (shops.isEmpty()) {
            return;
        }
        for (Shop shop : shops) {
            if (shop.getX() != null && shop.getY() != null) {
                stringRedisTemplate.opsForGeo().add(geoKey,
                        new org.springframework.data.geo.Point(shop.getX(), shop.getY()),
                        shop.getId().toString());
            }
        }
    }

    private String cacheVersion(String versionKey) {
        String version = stringRedisTemplate.opsForValue().get(versionKey);
        return version == null ? "0" : version;
    }
}
