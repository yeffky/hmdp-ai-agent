package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 地区（含地图 GEO 圆心坐标，供距离排序/地图初始化使用）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_district")
public class District implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属城市 id（关联 tb_city.id） */
    private Long cityId;

    /** 地区名，如 拱墅区/鼓楼区 */
    private String name;

    /** 地区中心经度（GCJ-02） */
    private Double centerX;

    /** 地区中心纬度（GCJ-02） */
    private Double centerY;

    /** 排序 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
