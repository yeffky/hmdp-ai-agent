package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SeckillCorrelationData;
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
import java.util.Arrays;
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
    private RabbitMQConfig rabbitMQConfig;

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
        if (result == null) {
            return Result.fail("秒杀服务暂不可用");
        }
        int r = result.intValue();
        if (r != 0) {
            if (r == 1) return Result.fail("库存不足");
            if (r == 2) return Result.fail("不能重复下单");
            if (r == 3) return Result.fail("秒杀尚未开始");
            if (r == 4) return Result.fail("秒杀已结束");
            return Result.fail("秒杀不可用");
        }

        // 2.发送消息到RabbitMQ（携带 CorrelationData：发布确认失败时可凭其回补 Redis 预留）
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    voucherOrder,
                    new SeckillCorrelationData(voucherOrder));
        } catch (Exception e) {
            // RabbitTemplate 可能在发布前同步抛错，此时不会触发异步 ConfirmCallback。
            log.error("秒杀订单消息同步发布失败: orderId={}", orderId, e);
            rabbitMQConfig.rollbackSeckillReservation(voucherOrder, "publish-exception");
            return Result.fail("秒杀服务暂不可用");
        }

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
        // RabbitMQ ACK 丢失后可能重复投递同一订单，已落库时直接幂等成功，不能再次扣库存。
        if (getById(voucherOrder.getId()) != null) {
            return;
        }
        Voucher voucher = voucherService.getById(voucherOrder.getVoucherId());
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherOrder.getVoucherId());
        LocalDateTime now = LocalDateTime.now();
        if (voucher == null || !Integer.valueOf(1).equals(voucher.getType())
                || !Integer.valueOf(1).equals(voucher.getStatus())
                || seckillVoucher == null
                || (seckillVoucher.getBeginTime() != null && seckillVoucher.getBeginTime().isAfter(now))
                || (seckillVoucher.getEndTime() != null && !seckillVoucher.getEndTime().isAfter(now))) {
            throw new RuntimeException("秒杀券已失效: voucherId=" + voucherOrder.getVoucherId());
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
            throw new RuntimeException("库存不足: voucherId=" + voucherOrder.getVoucherId());
        }

        save(voucherOrder);
        evictVoucherListCache(voucherOrder.getVoucherId());
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

        if (!doCancel(order)) {
            return Result.fail("订单状态已变化，无法取消");
        }
        return Result.ok();
    }

    @Override
    public Result refundOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = getById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getUserId().equals(userId)) return Result.fail("无权操作该订单");
        if (order.getStatus() == null || order.getStatus() != 2) return Result.fail("仅已支付未核销的订单可退款");

        // CAS 更新：仅当仍为已支付(2)时置为已退款(6)，防止并发下重复退款 / 与核销竞争。
        // 注意：用字符串列名而非 Lambda 方法引用（VoucherOrder::getStatus）——MyBatis-Plus 3.4.3
        // 解析 Lambda 需反射访问 java.lang.invoke.SerializedLambda，在 JDK17 强封装下会抛
        // InaccessibleObjectException，故此处与项目其他代码保持一致用字符串 SQL。
        boolean ok = update()
                .set("status", 6)
                .set("refund_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", 2)
                .update();
        if (!ok) return Result.fail("订单状态已变化，无法退款");

        // 秒杀券回补库存 + 放开一人一单
        releaseSeckillStock(order);
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
                .in("status", Arrays.asList(1, 2, 3))
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

    private boolean doCancel(VoucherOrder order) {
        boolean changed = update()
                .set("status", 4)
                .eq("id", order.getId())
                .eq("status", 1)
                .update();
        if (!changed) {
            return false;
        }
        releaseSeckillStock(order);
        return true;
    }

    /** 秒杀商品取消时回补 MySQL + Redis 库存，并放开一人一单限制 */
    private void releaseSeckillStock(VoucherOrder order) {
        Voucher voucher = voucherService.getById(order.getVoucherId());
        if (voucher == null || voucher.getType() == null || voucher.getType() != 1) return;
        boolean updated = seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", order.getVoucherId())
                .update();
        if (!updated) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + order.getVoucherId());
        stringRedisTemplate.opsForSet().remove(RedisConstants.SECKILL_ORDER_SET_KEY + order.getVoucherId(), order.getUserId().toString());
        evictVoucherListCache(order.getVoucherId());
    }

    private void evictVoucherListCache(Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher != null && voucher.getShopId() != null) {
            voucherService.evictVoucherListCache(voucher.getShopId());
        }
    }
}
