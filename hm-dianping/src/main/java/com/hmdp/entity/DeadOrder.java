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
 * 秒杀死信订单记录 — 重试超限转入 DLQ 后由 DeadLetterConsumer 落库
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_dead_order")
public class DeadOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 秒杀订单id（业务键，同一订单多次失败可各留一条审计记录）
     */
    private Long orderId;

    /**
     * 下单的用户id
     */
    private Long userId;

    /**
     * 购买的代金券id
     */
    private Long voucherId;

    /**
     * 失败原因（x-death 的 reason：rejected / expired / maxlen）
     */
    private String failReason;

    /**
     * 进入 DLQ 时已重试次数
     */
    private Integer retryCount;

    /**
     * 处理状态 0-待处理 1-已重放 2-已确认丢弃
     */
    private Integer status;

    /**
     * 进入 DLQ 时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（重放/丢弃时变化）
     */
    private LocalDateTime updateTime;
}
