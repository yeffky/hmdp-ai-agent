package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderServiceImpl.class);

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private IShopService shopService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Lazy
    @Resource
    private IVoucherOrderService proxy;

    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 1.执行lua脚本（原子校验库存 + 扣减 + 一人一单判断）
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 2.发送消息到RabbitMQ
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                voucherOrder);

        return Result.ok();
    }

    public void handleVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLock) {
            throw new RuntimeException("获取分布式锁失败: userId=" + userId + ", orderId=" + voucherOrder.getId());
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
            throw new RuntimeException("库存不足: voucherId=" + voucherOrder.getVoucherId());
        }

        save(voucherOrder);
    }

    // ========== 团购下单 / 支付 / 取消 / 订单列表 ==========

    @Override
    public Result createOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) return Result.fail("团购商品不存在");
        if (voucher.getStatus() != null && voucher.getStatus() != 1) return Result.fail("团购商品已下架");
        if (voucher.getType() != null && voucher.getType() == 1) return Result.fail("秒杀商品请走秒杀下单");

        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(1); // 待支付
        save(order);
        return Result.ok(String.valueOf(order.getId())); // 字符串返回，避免 JS 精度丢失
    }

    @Override
    public Result payOrder(Long orderId, Integer payType) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = getById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getUserId().equals(userId)) return Result.fail("无权操作该订单");
        if (order.getStatus() == null || order.getStatus() != 1) return Result.fail("订单状态不允许支付");

        VoucherOrder upd = new VoucherOrder();
        upd.setId(orderId);
        upd.setStatus(2); // 已支付
        upd.setPayTime(LocalDateTime.now());
        upd.setPayType(payType == null ? 1 : payType);
        updateById(upd);
        return Result.ok();
    }

    @Override
    public Result cancelOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = getById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getUserId().equals(userId)) return Result.fail("无权操作该订单");
        if (order.getStatus() == null || order.getStatus() != 1) return Result.fail("订单状态不允许取消");

        doCancel(order);
        return Result.ok();
    }

    @Override
    public Result queryMyOrders() {
        Long userId = UserHolder.getUser().getId();
        List<VoucherOrder> orders = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (VoucherOrder o : orders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(o.getId())); // 字符串返回，避免雪花 ID 在 JS 丢精度
            item.put("voucherId", o.getVoucherId());
            item.put("status", o.getStatus());
            item.put("payType", o.getPayType());
            item.put("createTime", o.getCreateTime());
            item.put("payTime", o.getPayTime());
            Voucher v = voucherService.getById(o.getVoucherId());
            if (v != null) {
                item.put("title", v.getTitle());
                item.put("image", v.getImage());
                item.put("payValue", v.getPayValue());
                item.put("actualValue", v.getActualValue());
                item.put("type", v.getType());
                Shop shop = shopService.getById(v.getShopId());
                item.put("shopName", shop != null ? shop.getName() : "");
            }
            result.add(item);
        }
        return Result.ok(result);
    }

    @Override
    public Result seckillStatus(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        return Result.ok(count != null && count > 0);
    }

    @Override
    public void cancelExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);
        List<VoucherOrder> expired = query()
                .eq("status", 1)
                .lt("create_time", deadline)
                .list();
        for (VoucherOrder o : expired) {
            try {
                doCancel(o);
            } catch (Exception e) {
                log.error("超时自动取消订单失败: orderId={}", o.getId(), e);
            }
        }
    }

    private void doCancel(VoucherOrder order) {
        VoucherOrder upd = new VoucherOrder();
        upd.setId(order.getId());
        upd.setStatus(4); // 已取消
        updateById(upd);
        releaseSeckillStock(order);
    }

    /** 秒杀商品取消时回补 MySQL + Redis 库存，并放开一人一单限制 */
    private void releaseSeckillStock(VoucherOrder order) {
        Voucher voucher = voucherService.getById(order.getVoucherId());
        if (voucher == null || voucher.getType() == null || voucher.getType() != 1) return;
        seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", order.getVoucherId())
                .update();
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + order.getVoucherId());
        stringRedisTemplate.opsForSet().remove("seckill:order:" + order.getVoucherId(), order.getUserId().toString());
    }
}
