package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.time.ZoneId;
import java.util.Date;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 团购列表缓存（Cache-Aside + 空值防穿透；券变更走 save/removeById 主动失效）
        String key = CACHE_VOUCHER_LIST_KEY + shopId;
        List<Voucher> vouchers = cacheClient.queryListWithPassThrough(key, Voucher.class,
                () -> getBaseMapper().queryVoucherOfShop(shopId),
                CACHE_VOUCHER_LIST_TTL, TimeUnit.MINUTES);
        return Result.ok(vouchers);
    }

    /** 覆盖 save：新增/更新券后失效该店铺团购列表缓存 */
    @Override
    public boolean save(Voucher entity) {
        boolean ok = super.save(entity);
        if (ok) {
            evictVoucherListCache(entity.getShopId());
        }
        return ok;
    }

    @Override
    public boolean updateById(Voucher entity) {
        Voucher oldVoucher = entity.getId() == null ? null : getById(entity.getId());
        boolean ok = super.updateById(entity);
        if (ok) {
            evictVoucherListCache(oldVoucher == null ? null : oldVoucher.getShopId());
            evictVoucherListCache(entity.getShopId() == null && oldVoucher != null
                    ? oldVoucher.getShopId() : entity.getShopId());
        }
        return ok;
    }

    /** 覆盖 removeById：删除券前先取 shopId 用于失效缓存 */
    @Override
    public boolean removeById(Serializable id) {
        Voucher v = getById(id);
        boolean ok = super.removeById(id);
        if (ok && v != null) {
            evictVoucherListCache(v.getShopId());
        }
        return ok;
    }

    @Override
    public void evictVoucherListCache(Long shopId) {
        if (shopId != null) {
            stringRedisTemplate.delete(CACHE_VOUCHER_LIST_KEY + shopId);
        }
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // MySQL 提交成功后再初始化 Redis，避免事务回滚后留下可售卖的孤儿库存 key。
        Runnable initializeRedis = () -> initializeSeckillRedis(voucher);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    initializeRedis.run();
                }
            });
        } else {
            initializeRedis.run();
        }
    }

    private void initializeSeckillRedis(Voucher voucher) {
        stringRedisTemplate.opsForValue().set(
                SECKILL_STOCK_KEY + voucher.getId(), String.valueOf(voucher.getStock()));
        if (voucher.getBeginTime() != null) {
            stringRedisTemplate.opsForValue().set(
                    SECKILL_BEGIN_KEY + voucher.getId(), String.valueOf(toEpochMillis(voucher.getBeginTime())));
        }
        if (voucher.getEndTime() != null) {
            stringRedisTemplate.opsForValue().set(
                    SECKILL_END_KEY + voucher.getId(), String.valueOf(toEpochMillis(voucher.getEndTime())));
            Date expireAt = Date.from(voucher.getEndTime().atZone(ZoneId.systemDefault()).toInstant());
            stringRedisTemplate.expireAt(SECKILL_STOCK_KEY + voucher.getId(), expireAt);
            stringRedisTemplate.expireAt(SECKILL_BEGIN_KEY + voucher.getId(), expireAt);
            stringRedisTemplate.expireAt(SECKILL_END_KEY + voucher.getId(), expireAt);
        }
    }

    private long toEpochMillis(java.time.LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}


