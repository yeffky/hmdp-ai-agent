package com.hmdp.utils;

import com.hmdp.entity.VoucherOrder;
import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * 秒杀下单消息的发布确认关联数据。
 *
 * 携带完整的 {@link VoucherOrder}，使 ConfirmCallback / ReturnsCallback 在发布失败时
 * 能凭 correlationData 反查订单信息，回补 Redis 库存与一人一单集合（Lua 阶段已扣减/记录）。
 */
public class SeckillCorrelationData extends CorrelationData {

    private final VoucherOrder voucherOrder;

    public SeckillCorrelationData(VoucherOrder voucherOrder) {
        super(String.valueOf(voucherOrder.getId()));
        this.voucherOrder = voucherOrder;
    }

    public VoucherOrder getVoucherOrder() {
        return voucherOrder;
    }
}
