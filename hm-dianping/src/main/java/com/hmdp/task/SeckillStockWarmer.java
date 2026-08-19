package com.hmdp.task;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.springframework.data.redis.core.ValueOperations;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import static com.hmdp.utils.RedisConstants.SECKILL_BEGIN_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_END_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 秒杀库存预热：应用启动时把上架中的秒杀券 MySQL 库存同步到 Redis。
 * 避免应用重启后 `seckill:stock:{id}` 缺失导致 Lua 脚本比较 nil 报错。
 * （xxl-job 的 seckillStockWarmup 任务做周期性兜底，本类做启动兜底）
 * 通过 MySQL JOIN 一次查询未过期秒杀券，避免应用层拼接超长 IN 列表。
 */
@Component
public class SeckillStockWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockWarmer.class);

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // 异步预热，不阻塞应用启动；期间秒杀由 Lua nil 兜底（返回"库存不足"）
        Thread t = new Thread(() -> {
            int n = warmUp();
            log.info("秒杀库存启动预热完成，预热 {} 个秒杀券", n);
        }, "seckill-stock-warmup");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 初始化上架且未过期的秒杀券库存到 Redis。
     *
     * 这里只能使用 setIfAbsent，不能用 MySQL 库存覆盖已有 Redis 库存，否则会把
     * 尚未落库的 Redis 预扣库存重新加回来，造成超卖。
     */
    public int warmUp() {
        List<SeckillVoucher> activeSvs = seckillVoucherService.listActiveForWarmup();
        LocalDateTime now = LocalDateTime.now();
        ValueOperations<String, String> values = stringRedisTemplate.opsForValue();
        int initialized = 0;
        for (SeckillVoucher sv : activeSvs) {
            writeTimeMetadata(values, sv);
            if (sv.getStock() != null && (sv.getBeginTime() == null || !sv.getBeginTime().isAfter(now))) {
                Boolean created = values.setIfAbsent(
                        SECKILL_STOCK_KEY + sv.getVoucherId(), String.valueOf(sv.getStock()));
                if (Boolean.TRUE.equals(created)) {
                    initialized++;
                }
                expireStockKey(sv);
            }
        }
        return initialized;
    }

    /** 清理已过期秒杀券的库存/时间元数据，避免过期 key 长期残留。 */
    public int clearExpiredKeys() {
        List<SeckillVoucher> expired = seckillVoucherService.list(new QueryWrapper<SeckillVoucher>()
                .le("end_time", LocalDateTime.now())
                .select("voucher_id"));
        for (SeckillVoucher sv : expired) {
            stringRedisTemplate.delete(SECKILL_STOCK_KEY + sv.getVoucherId());
            stringRedisTemplate.delete(SECKILL_BEGIN_KEY + sv.getVoucherId());
            stringRedisTemplate.delete(SECKILL_END_KEY + sv.getVoucherId());
        }
        return expired.size();
    }

    private void writeTimeMetadata(ValueOperations<String, String> values, SeckillVoucher sv) {
        if (sv.getBeginTime() != null) {
            values.set(SECKILL_BEGIN_KEY + sv.getVoucherId(), String.valueOf(toEpochMillis(sv.getBeginTime())));
        }
        if (sv.getEndTime() != null) {
            values.set(SECKILL_END_KEY + sv.getVoucherId(), String.valueOf(toEpochMillis(sv.getEndTime())));
            Date expireAt = Date.from(sv.getEndTime().atZone(ZoneId.systemDefault()).toInstant());
            stringRedisTemplate.expireAt(SECKILL_BEGIN_KEY + sv.getVoucherId(), expireAt);
            stringRedisTemplate.expireAt(SECKILL_END_KEY + sv.getVoucherId(), expireAt);
        }
    }

    private void expireStockKey(SeckillVoucher sv) {
        if (sv.getEndTime() != null) {
            Date expireAt = Date.from(sv.getEndTime().atZone(ZoneId.systemDefault()).toInstant());
            stringRedisTemplate.expireAt(SECKILL_STOCK_KEY + sv.getVoucherId(), expireAt);
        }
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
