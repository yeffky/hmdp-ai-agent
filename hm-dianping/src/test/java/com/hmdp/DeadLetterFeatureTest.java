package com.hmdp;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.DeadOrder;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.DeadOrderServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 死信处理功能测试（方案 B）。
 *
 * 前置条件：
 *   - 远程服务可达（Redis / MySQL / RabbitMQ）
 *   - 已执行 hmdp.sql 中 tb_dead_order 建表 DDL
 *   - 已删除旧 seckill.order.queue 让死信参数生效
 *
 * 运行: mvn test -Dtest=DeadLetterFeatureTest
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeadLetterFeatureTest {

    private static final Long TEST_USER_ID = 777777L;
    private static final Long TEST_VOUCHER_ID = 666L;

    @Resource
    private DeadOrderServiceImpl deadOrderService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    // ==================== DeadOrderService: redeliver ====================

    @Test
    @Order(1)
    @DisplayName("redeliver: 不存在的 id 应抛异常")
    void testRedeliverNonExistent() {
        assertThrows(RuntimeException.class, () -> deadOrderService.redeliver(-1L),
                "不存在的死信记录应抛异常");
    }

    @Test
    @Order(2)
    @DisplayName("redeliver: 待处理死信重放成功，状态置为已重放")
    void testRedeliverSuccess() {
        DeadOrder record = insertRecord(100001L);
        try {
            deadOrderService.redeliver(100001L);
            DeadOrder after = deadOrderService.getById(100001L);
            assertNotNull(after, "重放后记录应存在");
            assertEquals(1, after.getStatus(), "重放后状态应为 1-已重放");
        } finally {
            deadOrderService.removeById(100001L);
        }
    }

    @Test
    @Order(3)
    @DisplayName("redeliver: 已重放的死信再次重放应抛异常")
    void testRedeliverTwiceThrows() {
        DeadOrder record = insertRecord(100002L);
        try {
            deadOrderService.redeliver(100002L);
            assertThrows(RuntimeException.class, () -> deadOrderService.redeliver(100002L),
                    "已重放的死信不能再次重放");
        } finally {
            deadOrderService.removeById(100002L);
        }
    }

    // ==================== DeadOrderService: discard ====================

    @Test
    @Order(4)
    @DisplayName("discard: 确认丢弃后状态置为已丢弃")
    void testDiscard() {
        insertRecord(100003L);
        try {
            deadOrderService.discard(100003L);
            DeadOrder after = deadOrderService.getById(100003L);
            assertEquals(2, after.getStatus(), "丢弃后状态应为 2-已确认丢弃");
        } finally {
            deadOrderService.removeById(100003L);
        }
    }

    // ==================== RabbitMQ 投递 ====================

    @Test
    @Order(5)
    @DisplayName("DeadOrder 消息可正常投递到 DLX")
    void testPublishToDlx() {
        VoucherOrder order = new VoucherOrder();
        order.setId(100004L);
        order.setUserId(TEST_USER_ID);
        order.setVoucherId(TEST_VOUCHER_ID);

        assertDoesNotThrow(() -> rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_ORDER_DLX,
                RabbitMQConfig.SECKILL_ORDER_DLQ_ROUTING_KEY,
                order), "死信消息应能投递到 DLX");
    }

    // ==================== DLQ 消费者：直接落库 ====================

    @Test
    @Order(6)
    @DisplayName("DLQ 消费者: 死信直接落库形成日志，不再打回正常队列")
    void testDlqConsumerRecordsDirectly() throws Exception {
        long orderId = 900000000L + System.currentTimeMillis() % 100000000L;
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(TEST_USER_ID);
        order.setVoucherId(TEST_VOUCHER_ID);

        // 投递到 DLX → DLQ，携带已重试 3 次的标记
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_ORDER_DLX,
                RabbitMQConfig.SECKILL_ORDER_DLQ_ROUTING_KEY,
                order,
                msg -> {
                    msg.getMessageProperties().setHeader("x-retry-count", 3);
                    return msg;
                });

        // 轮询等待 DLQ 消费者落库（异步，最长 10s）
        DeadOrder record = null;
        for (int i = 0; i < 20; i++) {
            record = deadOrderService.getById(orderId);
            if (record != null) {
                break;
            }
            Thread.sleep(500);
        }

        try {
            assertNotNull(record, "死信应被 DLQ 消费者直接落库");
            assertEquals(orderId, record.getId(), "主键应为订单id");
            assertEquals(TEST_USER_ID, record.getUserId());
            assertEquals(TEST_VOUCHER_ID, record.getVoucherId());
            assertEquals(3, record.getRetryCount(), "应记录进入 DLQ 时的重试次数");
            assertEquals(0, record.getStatus(), "初始状态应为待处理");
        } finally {
            deadOrderService.removeById(orderId);
        }
    }

    // ==================== 辅助方法 ====================

    private DeadOrder insertRecord(long orderId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(TEST_USER_ID);
        order.setVoucherId(TEST_VOUCHER_ID);

        DeadOrder record = deadOrderService.buildRecord(order, "rejected", 3, 1);
        record.setCreateTime(LocalDateTime.now());
        deadOrderService.save(record);
        return record;
    }
}
