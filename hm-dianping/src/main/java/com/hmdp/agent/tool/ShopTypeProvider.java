package com.hmdp.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商家类型提供器 — 从 tb_shop_type 动态加载「类型ID→名称」映射，
 * 注入到 Agent 提示词与工具参数校验，避免硬编码的类型表与实际数据不一致。
 */
@Component
public class ShopTypeProvider {

    private static final Logger log = LoggerFactory.getLogger(ShopTypeProvider.class);

    @Resource
    private ShopTypeMapper shopTypeMapper;

    private volatile Map<Integer, String> typeMap = Map.of();

    @PostConstruct
    void init() {
        refresh();
    }

    public void refresh() {
        List<ShopType> list = shopTypeMapper.selectList(
                new QueryWrapper<ShopType>().orderByAsc("id"));
        LinkedHashMap<Integer, String> m = new LinkedHashMap<>();
        for (ShopType t : list) {
            m.put(t.getId().intValue(), t.getName());
        }
        typeMap = m;
        log.info("ShopTypeProvider loaded {} types: {}", m.size(), typeText());
    }

    public Map<Integer, String> typeMap() {
        return typeMap;
    }

    /** 渲染 "1=美食,2=KTV,3=丽人·美发,..." 供 LLM 提示词使用 */
    public String typeText() {
        return typeMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    public boolean isValidType(int id) {
        return typeMap.containsKey(id);
    }
}
