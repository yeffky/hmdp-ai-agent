package com.hmdp.controller;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        String key = "cache:shop_type";
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (CollUtil.isNotEmpty(shopTypeJsonList)) {
            List<ShopType> shopTypeList = shopTypeJsonList.stream()
                    .map((shopTypeJson) -> JSONUtil.toBean(shopTypeJson, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }

        List<ShopType> shopTypeList = typeService
                .query().orderByAsc("sort").list();

        if (CollUtil.isEmpty(shopTypeList)) {
            return Result.fail("商品类型不存在");
        }

        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList.stream().map((JSONUtil::toJsonStr)).collect(Collectors.toList()));
        // 类型几乎静态：长 TTL 兜底（数据变更可手动删 key 或重启后重建）
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TYPE_TTL, java.util.concurrent.TimeUnit.MINUTES);

        return Result.ok(shopTypeList);
    }
}
