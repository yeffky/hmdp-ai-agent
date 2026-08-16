package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /** 普通团购商品下单（不限量） */
    @PostMapping("/buy/{voucherId}")
    public Result buy(@PathVariable("voucherId") Long voucherId) {
        return voucherOrderService.createOrder(voucherId);
    }

    /** 支付订单（模拟余额/支付宝/微信），payType=1余额/2支付宝/3微信 */
    @PutMapping("/pay/{orderId}")
    public Result pay(@PathVariable("orderId") Long orderId,
                      @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        return voucherOrderService.payOrder(orderId, payType);
    }

    /** 取消待支付订单 */
    @PutMapping("/cancel/{orderId}")
    public Result cancel(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.cancelOrder(orderId);
    }

    /** 退款：已支付且未核销的订单 */
    @PutMapping("/refund/{orderId}")
    public Result refund(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.refundOrder(orderId);
    }

    /** 我的订单列表 */
    @GetMapping("/my")
    public Result myOrders() {
        return voucherOrderService.queryMyOrders();
    }

    /** 当前用户是否已购买该秒杀券（用于前端置灰"限时秒杀"按钮） */
    @GetMapping("/seckill/status/{voucherId}")
    public Result seckillStatus(@PathVariable("voucherId") Long voucherId) {
        return voucherOrderService.seckillStatus(voucherId);
    }
}
