package com.hmdp.agent.tool.dto;

import lombok.Data;

import java.util.List;

/**
 * geoSearch 结构化输出（工具输出 schema）。
 * 下游按此解析，避免自由文本 JSON 格式不可控。
 */
@Data
public class GeoSearchResult {

    /** 命中的商家列表（按距离升序） */
    private List<ShopHit> shops;
}
