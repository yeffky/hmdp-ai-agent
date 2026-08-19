package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.DeadOrder;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.DeadOrderMapper;
import com.hmdp.service.IDeadOrderService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.SeckillCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class DeadOrderServiceImpl extends ServiceImpl<DeadOrderMapper, DeadOrder> implements IDeadOrderService {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RabbitMQConfig rabbitMQConfig;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public void redeliver(Long id) {
        DeadOrder deadOrder = getById(id);
        if (deadOrder == null) {
            throw new RuntimeException("死信记录不存在: id=" + id);
        }
        if (deadOrder.getStatus() != 0) {
            throw new RuntimeException("该死信已处理，不能重复重放: id=" + id + ", status=" + deadOrder.getStatus());
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(deadOrder.getOrderId());
        voucherOrder.setUserId(deadOrder.getUserId());
        voucherOrder.setVoucherId(deadOrder.getVoucherId());

        SeckillCorrelationData correlationData = new SeckillCorrelationData(voucherOrder);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    voucherOrder,
                    msg -> {
                        // 重置重试预算，重新走完整消费流程
                        msg.getMessageProperties().setHeader("x-retry-count", 0);
                        return msg;
                    },
                    // 携带 CorrelationData：重放消息发布失败时同样可回补 Redis 预留
                    correlationData);
        } catch (Exception e) {
            rabbitMQConfig.rollbackSeckillReservation(voucherOrder, "dead-letter-publish-exception");
            throw new IllegalStateException("死信订单重放发送失败", e);
        }
        try {
            Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("死信订单重放消息未被 RabbitMQ 接受");
            }
        } catch (TimeoutException e) {
            throw new IllegalStateException("等待死信订单重放确认超时", e);
        } catch (Exception e) {
            throw new IllegalStateException("死信订单重放发送失败", e);
        }
        deadOrder.setStatus(1);
        updateById(deadOrder);
        log.info("死信订单已手动重放: orderId={}, userId={}, voucherId={}",
                deadOrder.getId(), deadOrder.getUserId(), deadOrder.getVoucherId());
    }

    @Override
    public void discard(Long id) {
        DeadOrder deadOrder = getById(id);
        if (deadOrder == null) {
            throw new RuntimeException("死信记录不存在: id=" + id);
        }
        VoucherOrder voucherOrder = new VoucherOrder()
                .setId(deadOrder.getOrderId())
                .setUserId(deadOrder.getUserId())
                .setVoucherId(deadOrder.getVoucherId());
        // 重放和丢弃互斥：只有确认数据库中没有订单时，丢弃才释放 Redis 预留。
        if (voucherOrderService.getById(deadOrder.getOrderId()) == null
                && !rabbitMQConfig.rollbackSeckillReservation(voucherOrder, "dead-letter-discard")) {
            throw new IllegalStateException("死信 Redis 预留回补失败，暂不能丢弃");
        }
        deadOrder.setStatus(2);
        updateById(deadOrder);
        log.info("死信订单已确认丢弃: orderId={}", id);
    }

    /**
     * 构建死信记录实体（供 DeadLetterConsumer 落库）
     */
    public DeadOrder buildRecord(VoucherOrder voucherOrder, String failReason, int retryCount) {
        DeadOrder deadOrder = new DeadOrder();
        deadOrder.setOrderId(voucherOrder.getId());
        deadOrder.setUserId(voucherOrder.getUserId());
        deadOrder.setVoucherId(voucherOrder.getVoucherId());
        deadOrder.setFailReason(failReason);
        deadOrder.setRetryCount(retryCount);
        deadOrder.setStatus(0);
        deadOrder.setCreateTime(LocalDateTime.now());
        return deadOrder;
    }
}
