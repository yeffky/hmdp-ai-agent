package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * queryShopByName / queryShopByType / queryShopsForMap 纯单元测试：mock ShopMapper + Redis（缓存 miss 走 dbFallback），
 * 校验 MyBatis-Plus 条件包装器。
 */
class ShopServiceImplTest {

    private ShopMapper shopMapper;
    private ShopServiceImpl shopService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        shopMapper = mock(ShopMapper.class);
        shopService = new ShopServiceImpl();
        ReflectionTestUtils.setField(shopService, "baseMapper", shopMapper);
        // 缓存层：mock Redis（list 缓存 miss → 走 dbFallback，验证条件包装器）
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(shopService, "stringRedisTemplate", redis);
        CacheClient cacheClient = new CacheClient(redis);
        ReflectionTestUtils.setField(shopService, "cacheClient", cacheClient);
    }

    private void stubPage() {
        Page<Shop> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(new Shop()));
        when(shopMapper.selectPage(any(Page.class), any())).thenReturn(page);
    }

    private String capturedSqlSegment() {
        ArgumentCaptor<QueryWrapper<Shop>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(shopMapper).selectPage(any(Page.class), captor.capture());
        return captor.getValue().getSqlSegment();
    }

    @Test
    void queryShopByName_withTypeId_filtersByTypeAndName() {
        stubPage();
        shopService.queryShopByName("美食", 1, 5, null, null);
        String sql = capturedSqlSegment();
        assertTrue(sql.contains("type_id"), "带 typeId 时应包含 type_id 过滤, actual: " + sql);
        assertTrue(sql.contains("LIKE"), "应按名称模糊匹配, actual: " + sql);
    }

    @Test
    void queryShopByName_withoutTypeId_onlyFiltersByName() {
        stubPage();
        shopService.queryShopByName("美食", 1, null, null, null);
        String sql = capturedSqlSegment();
        assertFalse(sql.contains("type_id"), "无 typeId 时不应有 type_id 过滤, actual: " + sql);
        assertTrue(sql.contains("LIKE"), "应按名称模糊匹配, actual: " + sql);
    }

    @Test
    void queryShopByName_withDistrictId_filtersByDistrict() {
        stubPage();
        shopService.queryShopByName("美食", 1, null, 2L, null);
        String sql = capturedSqlSegment();
        assertTrue(sql.contains("district_id"), "带 districtId 时应包含 district_id 过滤, actual: " + sql);
        assertTrue(sql.contains("LIKE"), "应按名称模糊匹配, actual: " + sql);
    }

    @Test
    void queryShopByName_sortByComments_ordersByCommentsDesc() {
        stubPage();
        shopService.queryShopByName("美食", 1, null, null, "comments");
        String sql = capturedSqlSegment();
        assertTrue(sql.contains("ORDER BY comments"), "按人气排序应包含 ORDER BY comments, actual: " + sql);
    }

    @Test
    void queryShopByType_sortByScore_ordersByScoreDesc() {
        stubPage();
        shopService.queryShopByType(1, 1, null, null, 2L, "score", null);
        String sql = capturedSqlSegment();
        assertTrue(sql.contains("ORDER BY score"), "按评分排序应包含 ORDER BY score, actual: " + sql);
        assertTrue(sql.contains("district_id"), "应包含 district_id 过滤, actual: " + sql);
    }

    @Test
    void queryShopByType_sortByEmpty_noOrderBy() {
        stubPage();
        shopService.queryShopByType(1, 1, null, null, null, "", null);
        String sql = capturedSqlSegment();
        assertFalse(sql.contains("ORDER BY"), "综合排序不应带 ORDER BY, actual: " + sql);
    }

    @Test
    void queryShopByType_withFoodCategory_filtersBySubCategory() {
        stubPage();
        shopService.queryShopByType(1, 1, null, null, null, "", "快餐小吃");
        String sql = capturedSqlSegment();
        assertTrue(sql.contains("food_category"), "带 foodCategory 时应包含 food_category 过滤, actual: " + sql);
        // eq 值为参数化占位符，字面值在参数 Map 中
        ArgumentCaptor<QueryWrapper<Shop>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(shopMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("快餐小吃"),
                "应按细分过滤（参数值=快餐小吃）");
    }

    @Test
    void queryShopByType_withoutFoodCategory_noFoodCategoryFilter() {
        stubPage();
        shopService.queryShopByType(1, 1, null, null, null, "", null);
        String sql = capturedSqlSegment();
        assertFalse(sql.contains("food_category"), "无 foodCategory 时不应有 food_category 过滤, actual: " + sql);
    }

    @Test
    void queryShopsForMap_withDistrictId_filtersByDistrictAndType() {
        stubList();
        shopService.queryShopsForMap(2L, 1);
        ArgumentCaptor<QueryWrapper<Shop>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(shopMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("district_id"), "地图查询应包含 district_id 过滤, actual: " + sql);
        assertTrue(sql.contains("type_id"), "地图查询应包含 type_id 过滤, actual: " + sql);
    }

    private void stubList() {
        when(shopMapper.selectList(any())).thenReturn(Collections.singletonList(new Shop()));
    }
}
