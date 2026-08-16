package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId) throws InterruptedException;

    void createVoucherOrder(VoucherOrder voucherOrder);

    /** 普通团购商品下单（不限量），创建待支付订单 */
    Result createOrder(Long voucherId);

    /** 支付订单（模拟余额/支付宝/微信），置为已支付 */
    Result payOrder(Long orderId, Integer payType);

    /** 取消订单（待支付），若为秒杀商品则回补库存 */
    Result cancelOrder(Long orderId);

    /** 退款（已支付且未核销），置为已退款，若为秒杀商品则回补库存 */
    Result refundOrder(Long orderId);

    /** 当前用户的订单列表（含券/店铺信息） */
    Result queryMyOrders();

    /** 当前用户是否已购买该秒杀券（含待支付/已支付等任意订单） */
    Result seckillStatus(Long voucherId);

    /** 定时扫描：待支付超过 15 分钟自动取消并回补库存 */
    void cancelExpiredOrders();
}
