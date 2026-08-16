package com.hmdp.config;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SeckillCorrelationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_ORDER_DLX = "seckill.order.dlx";
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlq";
    public static final String SECKILL_ORDER_DLQ_ROUTING_KEY = "seckill.order.dlq";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Bean
    public Queue seckillOrderQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", SECKILL_ORDER_DLX);
        args.put("x-dead-letter-routing-key", SECKILL_ORDER_DLQ_ROUTING_KEY);
        return new Queue(SECKILL_ORDER_QUEUE, true, false, false, args);
    }

    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(SECKILL_ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public Queue seckillOrderDlq() {
        return new Queue(SECKILL_ORDER_DLQ, true);
    }

    @Bean
    public DirectExchange seckillOrderDlx() {
        return new DirectExchange(SECKILL_ORDER_DLX, true, false);
    }

    @Bean
    public Binding seckillOrderDlqBinding() {
        return BindingBuilder.bind(seckillOrderDlq())
                .to(seckillOrderDlx())
                .with(SECKILL_ORDER_DLQ_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        // mandatory=true：消息无法路由到队列时 Broker 退回并触发 ReturnsCallback
        // （配合 application.yaml 的 publisher-returns: true）
        rabbitTemplate.setMandatory(true);

        // 发布确认：Broker 收到并持久化消息后异步回调 ack/nack
        // （配合 application.yaml 的 publisher-confirm-type: correlated）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("秒杀消息发布确认成功: orderId={}",
                        correlationData != null ? correlationData.getId() : "unknown");
                return;
            }
            log.error("秒杀消息发布确认失败(nack), cause={}, correlationData={}",
                    cause, correlationData != null ? correlationData.getId() : "null");
            rollbackSeckillReservation(extractOrder(correlationData), "broker-nack: " + cause);
        });

        // 消息不可路由（交换机无匹配队列/路由键）时回调
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("秒杀消息不可路由被退回: exchange={}, routingKey={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
            rollbackSeckillReservation(deserializeVoucherOrder(returned.getMessage(), messageConverter),
                    "unroutable: " + returned.getReplyText());
        });

        return rabbitTemplate;
    }

    /** 从 ConfirmCallback 的 correlationData 中取订单（秒杀消息携带 SeckillCorrelationData） */
    private VoucherOrder extractOrder(CorrelationData correlationData) {
        return correlationData instanceof SeckillCorrelationData
                ? ((SeckillCorrelationData) correlationData).getVoucherOrder()
                : null;
    }

    /** 从退回的原始消息反序列化 VoucherOrder（ReturnsCallback 拿不到 correlationData，需从消息体还原） */
    private VoucherOrder deserializeVoucherOrder(Message message, Jackson2JsonMessageConverter converter) {
        if (message == null) return null;
        try {
            Object body = converter.fromMessage(message);
            return body instanceof VoucherOrder ? (VoucherOrder) body : null;
        } catch (Exception e) {
            log.warn("反序列化退回消息失败, body={}", new String(message.getBody(), StandardCharsets.UTF_8), e);
            return null;
        }
    }

    /**
     * 发布失败回补：Lua 阶段已扣 Redis 库存并记录一人一单，但消息未到达 Broker / 无法路由，
     * 订单永远不会落库，必须把 Redis 的预扣状态回滚，否则用户无法再买、库存被"吞"。
     */
    private void rollbackSeckillReservation(VoucherOrder order, String reason) {
        if (order == null) {
            // 非秒杀消息（或未携带关联数据），无回补上下文，仅记日志
            log.warn("发布失败但无秒杀上下文可回补, reason={}", reason);
            return;
        }
        try {
            stringRedisTemplate.opsForValue().increment(
                    RedisConstants.SECKILL_STOCK_KEY + order.getVoucherId());
            stringRedisTemplate.opsForSet().remove(
                    RedisConstants.SECKILL_ORDER_SET_KEY + order.getVoucherId(),
                    order.getUserId().toString());
            log.error("已回补秒杀Redis预留: orderId={}, userId={}, voucherId={}, reason={}",
                    order.getId(), order.getUserId(), order.getVoucherId(), reason);
        } catch (Exception e) {
            log.error("回补秒杀Redis预留失败: orderId={}", order.getId(), e);
        }
    }
}
