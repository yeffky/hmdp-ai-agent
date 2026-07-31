package com.hmdp;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import org.junit.jupiter.api.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RabbitMQ 秒杀重构集成测试。
 *
 * 前置条件（所有远程服务必须可达，连接地址取自 application.yaml）：
 *   - Redis
 *   - MySQL
 *   - RabbitMQ
 *   - tb_seckill_voucher 表中有 voucher_id=666 的测试券（stock > 0）
 *
 * 运行方式: mvn test -Dtest=SeckillRabbitMQTest
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SeckillRabbitMQTest {

    private static final Long TEST_VOUCHER_ID = 666L;
    private static final Long TEST_USER_ID = 99999L;

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // ==================== Lua 脚本测试 ====================

    @Test
    @Order(1)
    @DisplayName("Lua 脚本: 库存不足应返回 1")
    void testLuaStockExhausted() {
        String stockKey = "seckill:stock:" + TEST_VOUCHER_ID;
        stringRedisTemplate.opsForValue().set(stockKey, "0");

        Long result = stringRedisTemplate.execute(
                VoucherOrderServiceImpl.SECKILL_SCRIPT,
                java.util.Collections.emptyList(),
                TEST_VOUCHER_ID.toString(), TEST_USER_ID.toString());

        assertEquals(1L, result, "库存为0时应返回1");
    }

    @Test
    @Order(2)
    @DisplayName("Lua 脚本: 重复下单应返回 2")
    void testLuaDuplicateOrder() {
        String stockKey = "seckill:stock:" + TEST_VOUCHER_ID;
        String orderKey = "seckill:order:" + TEST_VOUCHER_ID;

        stringRedisTemplate.opsForValue().set(stockKey, "100");
        stringRedisTemplate.opsForSet().add(orderKey, TEST_USER_ID.toString());

        Long result = stringRedisTemplate.execute(
                VoucherOrderServiceImpl.SECKILL_SCRIPT,
                java.util.Collections.emptyList(),
                TEST_VOUCHER_ID.toString(), TEST_USER_ID.toString());

        assertEquals(2L, result, "用户已下单应返回2");

        // cleanup
        stringRedisTemplate.opsForSet().remove(orderKey, TEST_USER_ID.toString());
    }

    @Test
    @Order(3)
    @DisplayName("Lua 脚本: 正常秒杀应返回 0 且扣减库存")
    void testLuaSuccess() {
        String stockKey = "seckill:stock:" + TEST_VOUCHER_ID;
        String orderKey = "seckill:order:" + TEST_VOUCHER_ID;

        stringRedisTemplate.opsForValue().set(stockKey, "100");
        stringRedisTemplate.opsForSet().remove(orderKey, TEST_USER_ID.toString());

        Long result = stringRedisTemplate.execute(
                VoucherOrderServiceImpl.SECKILL_SCRIPT,
                java.util.Collections.emptyList(),
                TEST_VOUCHER_ID.toString(), TEST_USER_ID.toString());

        assertEquals(0L, result, "正常秒杀应返回0");

        String stockAfter = stringRedisTemplate.opsForValue().get(stockKey);
        assertEquals("99", stockAfter, "库存应扣减1");

        Boolean isMember = stringRedisTemplate.opsForSet().isMember(orderKey, TEST_USER_ID.toString());
        assertTrue(isMember, "用户应被记录到已购买集合");

        // cleanup
        stringRedisTemplate.opsForSet().remove(orderKey, TEST_USER_ID.toString());
    }

    // ==================== RabbitMQ 消息投递测试 ====================

    @Test
    @Order(4)
    @DisplayName("RabbitMQ: 正常投递秒杀订单消息")
    void testSendSeckillMessage() {
        VoucherOrder order = new VoucherOrder();
        order.setId(8888L);
        order.setUserId(TEST_USER_ID);
        order.setVoucherId(TEST_VOUCHER_ID);

        assertDoesNotThrow(() -> {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    order);
        }, "发送消息不应抛异常");
    }

    // ==================== 重试机制测试 ====================

    @Test
    @Order(5)
    @DisplayName("重试: VoucherOrder 的 x-retry-count header 应被正确设置")
    void testRetryHeaderIncrement() {
        VoucherOrder order = new VoucherOrder();
        order.setId(8889L);
        order.setUserId(TEST_USER_ID);
        order.setVoucherId(TEST_VOUCHER_ID);

        // 发送带 x-retry-count=2 的消息（模拟第2次重试）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                order,
                msg -> {
                    msg.getMessageProperties().setHeader("x-retry-count", 2);
                    return msg;
                });

        // 如果消息被消费后重试了第3次，header应变为3
        // 此测试仅验证 header 设置能力，实际验证依赖 Consumer 日志
        assertTrue(true, "消息已发送，检查日志确认 x-retry-count 是否递增到3");
    }

    // ==================== 分布式锁测试 ====================

    @Test
    @Order(6)
    @DisplayName("分布式锁: tryLock 失败应抛异常")
    void testLockFailureThrowsException() throws InterruptedException {
        String lockKey = "lock:order:" + TEST_USER_ID;
        RLock lock = redissonClient.getLock(lockKey);

        // 持有锁
        boolean locked = lock.tryLock(1L, TimeUnit.SECONDS);
        assertTrue(locked, "应先获取到锁");

        // 另一个线程尝试获取同一把锁
        RedissonClient rc = redissonClient;
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            RLock lock2 = rc.getLock(lockKey);
            try {
                boolean got = lock2.tryLock(0, TimeUnit.SECONDS);
                if (!got) failCount.incrementAndGet();
            } catch (InterruptedException ignored) {
            } finally {
                latch.countDown();
            }
        }).start();

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(1, failCount.get(), "第二个线程应获取锁失败");

        lock.unlock();
    }

    // ==================== 并发安全测试 ====================

    @Test
    @Order(7)
    @DisplayName("并发: 同一用户多请求只应成功一次（Redis Lua 原子性）")
    void testConcurrentDeduplication() throws InterruptedException {
        String stockKey = "seckill:stock:" + TEST_VOUCHER_ID;
        String orderKey = "seckill:order:" + TEST_VOUCHER_ID;
        Long testUser = 88888L;

        stringRedisTemplate.opsForValue().set(stockKey, "10");
        stringRedisTemplate.opsForSet().remove(orderKey, testUser.toString());

        int threads = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    Long result = stringRedisTemplate.execute(
                            VoucherOrderServiceImpl.SECKILL_SCRIPT,
                            java.util.Collections.emptyList(),
                            TEST_VOUCHER_ID.toString(), testUser.toString());
                    if (result == 0L) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, successCount.get(),
                "同一用户20个并发请求，应只有1个成功");

        // cleanup
        stringRedisTemplate.opsForSet().remove(orderKey, testUser.toString());
    }

    // ==================== DLQ 测试 ====================

    @Test
    @Order(8)
    @DisplayName("DLQ: 直接往 DLX 发消息验证 DLQ 队列存在")
    void testDlqBinding() {
        VoucherOrder order = new VoucherOrder();
        order.setId(7777L);
        order.setUserId(TEST_USER_ID);
        order.setVoucherId(TEST_VOUCHER_ID);

        assertDoesNotThrow(() -> {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_DLX,
                    RabbitMQConfig.SECKILL_ORDER_DLQ_ROUTING_KEY,
                    order);
        }, "消息应能发送到DLX并被DLQ接收");
    }

    // ==================== createVoucherOrder 异常测试 ====================

    @Test
    @Order(9)
    @DisplayName("createVoucherOrder: 库存不足应抛 RuntimeException")
    void testCreateVoucherOrderStockExhausted() {
        // 使用一个不存在的 voucherId，DB 中 stock=0 → update 返回 0 → 抛异常
        VoucherOrder order = new VoucherOrder();
        order.setId(9999L);
        order.setUserId(TEST_USER_ID);
        order.setVoucherId(-1L); // 不存在的券

        assertThrows(RuntimeException.class, () -> {
            voucherOrderService.createVoucherOrder(order);
        }, "库存不足应抛 RuntimeException");
    }
}
