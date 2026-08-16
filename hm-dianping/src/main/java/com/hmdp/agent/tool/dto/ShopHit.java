package com.hmdp.agent.tool.dto;

import lombok.Data;

/**
 * geoSearch 命中的商家（结构化输出条目）。
 */
@Data
public class ShopHit {

    /** 对外混淆 ID（Sqids 字符串），非数据库真实 ID */
    private String id;
    private String name;
    private Integer score;
    private Long avgPrice;
    private String area;
    private String address;

    /** 与用户定位的距离（米） */
    private Double distance;
}
