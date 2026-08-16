package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商铺名称
     */
    private String name;

    /**
     * 商铺类型的id
     */
    private Long typeId;

    /**
     * 所属地区id（关联 tb_district.id）
     */
    private Long districtId;

    /**
     * 美食细分（美食类门店）：奶茶咖啡/快餐小吃/火锅/烧烤烤肉/地方菜系/异域料理/自助餐/海鲜/面包蛋糕/食品生鲜
     */
    private String foodCategory;

    /**
     * 商铺图片，多个图片以','隔开
     */
    private String images;

    /**
     * 商圈，例如陆家嘴
     */
    private String area;

    /**
     * 地址
     */
    private String address;

    /**
     * 经度
     */
    private Double x;

    /**
     * 维度
     */
    private Double y;

    /**
     * 均价，取整数
     */
    private Long avgPrice;

    /**
     * 销量
     */
    private Integer sold;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 评分，1~5分，乘10保存，避免小数
     */
    private Integer score;

    /**
     * 营业时间，例如 10:00-22:00
     */
    private String openHours;

    /**
     * 是否支持排队取号：1=支持，0=不支持
     */
    private Integer queueEnabled;

    /**
     * 菜品/环境/服务描述（美食类门店规则生成）
     */
    private String description;

    /**
     * 是否支持停车：1=有，0=无
     */
    private Integer hasParking;

    /**
     * 是否儿童友好：1=是，0=否
     */
    private Integer childFriendly;

    /**
     * 是否宠物友好：1=是，0=否
     */
    private Integer petFriendly;

    /**
     * 最多容纳人数
     */
    private Integer maxSeats;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


    @TableField(exist = false)
    private Double distance;
}
