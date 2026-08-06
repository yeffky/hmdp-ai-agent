package com.hmdp.task;

import com.hmdp.service.IVoucherOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 待支付订单超时自动取消（15 分钟未支付 → 取消并回补秒杀库存）。
 */
@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Scheduled(fixedDelay = 60000)
    public void cancelExpiredOrders() {
        try {
            voucherOrderService.cancelExpiredOrders();
        } catch (Exception e) {
            log.error("待支付订单超时扫描失败", e);
        }
    }
}
