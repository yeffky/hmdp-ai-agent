package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.City;
import com.hmdp.entity.District;
import com.hmdp.service.ICityService;
import com.hmdp.service.IDistrictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 城市/地区（首页左上角定位选择器 + 地图初始化）
 */
@RestController
@RequestMapping("/region")
public class RegionController {

    @Resource
    private ICityService cityService;

    @Resource
    private IDistrictService districtService;

    /**
     * 城市及其下属地区列表（地区含 GEO 圆心坐标）
     */
    @GetMapping("/list")
    public Result list() {
        List<City> cities = cityService.list(new QueryWrapper<City>().orderByAsc("sort"));
        List<District> districts = districtService.list(new QueryWrapper<District>().orderByAsc("sort"));
        Map<Long, List<District>> byCity = districts.stream()
                .collect(Collectors.groupingBy(District::getCityId));
        cities.forEach(c -> c.setDistricts(byCity.getOrDefault(c.getId(), Collections.emptyList())));
        return Result.ok(cities);
    }
}
