package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_queue_ticket")
public class QueueTicket implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 商铺id */
    private Long shopId;

    /** 用户id */
    private Long userId;

    /** 当日排队号 */
    private Integer queueNumber;

    /** 用餐人数 */
    private Integer peopleCount;

    /** 状态: 0排队中/1已叫号/2已取消/3已完成 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 取号时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
