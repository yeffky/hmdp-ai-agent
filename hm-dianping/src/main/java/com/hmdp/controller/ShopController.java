package com.hmdp.controller;


import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y,
            @RequestParam(value = "districtId", required = false) Long districtId,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        return shopService.queryShopByType(typeId, current, x, y, districtId, sortBy);
    }

    /**
     * 根据商铺类型分页查询商铺信息（不按坐标过滤，用于管理场景）
     */
    @GetMapping("/of/type/no-geo")
    public Result queryShopByTypeNoGeo(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "districtId", required = false) Long districtId,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        return shopService.queryShopByType(typeId, current, null, null, districtId, sortBy);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "typeId", required = false) Integer typeId,
            @RequestParam(value = "districtId", required = false) Long districtId,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        // 按名称分页查询；typeId 限定分类，districtId 限定地区，sortBy 支持人气/评分排序
        return shopService.queryShopByName(name, current, typeId, districtId, sortBy);
    }

    /**
     * 地图标注用：按地区（可选分类）返回全部商铺
     */
    @GetMapping("/map")
    public Result queryShopsForMap(
            @RequestParam(value = "districtId", required = false) Long districtId,
            @RequestParam(value = "typeId", required = false) Integer typeId
    ) {
        return shopService.queryShopsForMap(districtId, typeId);
    }
}
