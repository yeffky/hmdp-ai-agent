package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y, Long districtId, String sortBy, String foodCategory);

    /** 美食细分列表（含各细分门店数），供前端美食筛选 */
    Result foodCategories(Long districtId);

    /** 按名称搜索商铺；typeId 限定分类，districtId 限定地区，sortBy 支持 comments/score 服务端排序 */
    Result queryShopByName(String name, Integer current, Integer typeId, Long districtId, String sortBy);

    /** 地图标注用：按地区（可选分类）返回全部商铺 */
    Result queryShopsForMap(Long districtId, Integer typeId);
}
