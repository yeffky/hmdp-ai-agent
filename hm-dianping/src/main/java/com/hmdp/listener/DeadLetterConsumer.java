package com.hmdp.listener;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.DeadOrderServiceImpl;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 死信队列消费者 — 死信直接落库 tb_dead_order 形成审计日志。
 *
 * 设计：进入 DLQ 的消息已由主消费端完成有限重试（3次+退避），视为最终失败，
 * 不再打回正常队列。直接持久化，由人工通过 DeadLetterController 决定重放/丢弃。
 * DB 瞬时故障时带 x-dlq-process-count 受控重试，超限丢弃并留原始消息日志，杜绝无限循环。
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    /** DLQ 自身处理失败的最大重试次数（超过则丢弃并 FATAL 日志，杜绝无限循环） */
    private static final int MAX_PROCESS_RETRY = 3;

    @Resource
    private DeadOrderServiceImpl deadOrderService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_DLQ, concurrency = "1")
    public void handleDeadLetter(VoucherOrder voucherOrder, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String failReason = extractDeathReason(message);

        try {
            // 保留 Redis 预留：人工重放会绕过 Lua，直接进入消费队列，不能在此处提前回补。
            // 人工确认丢弃时再执行幂等回补。
            recordPermanent(voucherOrder, message, failReason);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleProcessFailure(voucherOrder, message, channel, deliveryTag, e);
        }
    }

    /** DLQ 处理失败：带 x-dlq-process-count 受控重试，超限丢弃并留原始消息日志 */
    private void handleProcessFailure(VoucherOrder voucherOrder, Message message,
                                      Channel channel, long deliveryTag, Exception e) {
        int processCount = readProcessCount(message);
        try {
            if (processCount < MAX_PROCESS_RETRY) {
                log.error("DLQ 处理失败，受控重试(第{}次): orderId={}", processCount + 1, voucherOrder.getId(), e);
                // 重投到 DLX（保留原 headers + process-count+1），ack 当前，避免无限 requeue
                MessageProperties copy = new MessageProperties();
                Map<String, Object> originHeaders = message.getMessageProperties().getHeaders();
                if (originHeaders != null) {
                    copy.setHeaders(new HashMap<>(originHeaders));
                }
                copy.setContentType(message.getMessageProperties().getContentType());
                copy.setHeader("x-dlq-process-count", processCount + 1);
                rabbitTemplate.send(
                        RabbitMQConfig.SECKILL_ORDER_DLX,
                        RabbitMQConfig.SECKILL_ORDER_DLQ_ROUTING_KEY,
                        new Message(message.getBody(), copy));
            } else {
                log.error("DLQ 处理失败已超限，丢弃该死信，原始消息保留在日志: orderId={}, payload={}",
                        voucherOrder.getId(),
                        new String(message.getBody(), StandardCharsets.UTF_8), e);
            }
            channel.basicAck(deliveryTag, false);
        } catch (IOException ioEx) {
            log.error("DLQ Nack失败: orderId={}", voucherOrder.getId(), ioEx);
        }
    }

    /** 永久死信落库 tb_dead_order，status=0 待处理（自增主键，同订单多次失败各留一条审计记录） */
    private void recordPermanent(VoucherOrder voucherOrder, Message message, String failReason) {
        Integer retryCount = message.getMessageProperties().getHeader("x-retry-count");
        deadOrderService.save(deadOrderService.buildRecord(
                voucherOrder, failReason, retryCount == null ? 0 : retryCount));
        log.error("死信订单永久记录: orderId={}, userId={}, voucherId={}, reason={}, retryCount={}",
                voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId(),
                failReason, retryCount);
    }

    private int readProcessCount(Message message) {
        Integer count = message.getMessageProperties().getHeader("x-dlq-process-count");
        return count == null ? 0 : count;
    }

    private String extractDeathReason(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath != null && !xDeath.isEmpty() && xDeath.get(0).get("reason") != null) {
            return String.valueOf(xDeath.get(0).get("reason"));
        }
        return "unknown";
    }
}
