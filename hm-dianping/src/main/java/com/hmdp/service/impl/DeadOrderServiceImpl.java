package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.DeadOrder;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.DeadOrderMapper;
import com.hmdp.service.IDeadOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class DeadOrderServiceImpl extends ServiceImpl<DeadOrderMapper, DeadOrder> implements IDeadOrderService {

    @Resource
    private RabbitTemplate rabbitTemplate;

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
        voucherOrder.setId(deadOrder.getId());
        voucherOrder.setUserId(deadOrder.getUserId());
        voucherOrder.setVoucherId(deadOrder.getVoucherId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                voucherOrder,
                msg -> {
                    // 重置重试预算，重新走完整消费流程
                    msg.getMessageProperties().setHeader("x-retry-count", 0);
                    return msg;
                });

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
        deadOrder.setStatus(2);
        updateById(deadOrder);
        log.info("死信订单已确认丢弃: orderId={}", id);
    }

    /**
     * 构建死信记录实体（供 DeadLetterConsumer 落库）
     */
    public DeadOrder buildRecord(VoucherOrder voucherOrder, String failReason,
                                 int retryCount, int dlqRounds) {
        DeadOrder deadOrder = new DeadOrder();
        deadOrder.setId(voucherOrder.getId());
        deadOrder.setUserId(voucherOrder.getUserId());
        deadOrder.setVoucherId(voucherOrder.getVoucherId());
        deadOrder.setFailReason(failReason);
        deadOrder.setRetryCount(retryCount);
        deadOrder.setDlqRounds(dlqRounds);
        deadOrder.setStatus(0);
        deadOrder.setCreateTime(LocalDateTime.now());
        return deadOrder;
    }
}
