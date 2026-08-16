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
 * 店铺评论
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_comment")
public class ShopComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 商铺id */
    private Long shopId;

    /** 评论用户id */
    private Long userId;

    /** 评论内容 */
    private String content;

    /** 评分 1-5 */
    private Integer rating;

    /** 点赞数 */
    private Integer liked;

    /** 状态 0正常/1举报/2禁止查看 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 评论用户昵称（接口补全，非表字段） */
    @TableField(exist = false)
    private String nickName;

    /** 评论用户头像（接口补全，非表字段） */
    @TableField(exist = false)
    private String icon;

    /** 所属店铺名称（接口补全，非表字段） */
    @TableField(exist = false)
    private String shopName;
}
