package com.hmdp.listener;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

@Slf4j
@Component
public class VoucherOrderConsumer {

    private static final int MAX_RETRY = 3;
    private static final long[] BACKOFF_DELAYS = {2000, 5000, 10000};

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(VoucherOrder voucherOrder, Message message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            log.info("收到秒杀订单消息: orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
            voucherOrderService.handleVoucherOrder(voucherOrder);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理秒杀订单失败: orderId={}", voucherOrder.getId(), e);
            handleRetryOrDlq(voucherOrder, message, channel, deliveryTag);
        }
    }

    private void handleRetryOrDlq(VoucherOrder voucherOrder, Message message,
                                   Channel channel, long deliveryTag) {
        Integer retryCount = message.getMessageProperties().getHeader("x-retry-count");
        if (retryCount == null) {
            retryCount = 0;
        }
        final int nextRetry = retryCount + 1;

        if (retryCount < MAX_RETRY) {
            long delay = BACKOFF_DELAYS[retryCount];
            log.warn("秒杀订单消费失败，第{}次重试，{}ms后重试: orderId={}",
                    retryCount + 1, delay, voucherOrder.getId());
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    voucherOrder,
                    msg -> {
                        msg.getMessageProperties().setHeader("x-retry-count", nextRetry);
                        return msg;
                    });
            ackAndLog(channel, deliveryTag, voucherOrder.getId());
        } else {
            log.error("秒杀订单消费失败，已重试{}次，转入DLQ: orderId={}",
                    MAX_RETRY, voucherOrder.getId());
            // requeue=false + 队列已声明 x-dead-letter-exchange → RabbitMQ 原生路由到 DLQ
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("消息Nack失败，无法进入DLQ: orderId={}", voucherOrder.getId(), ex);
            }
        }
    }

    private void ackAndLog(Channel channel, long deliveryTag, long orderId) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ex) {
            log.error("消息ACK失败: orderId={}", orderId, ex);
        }
    }
}
