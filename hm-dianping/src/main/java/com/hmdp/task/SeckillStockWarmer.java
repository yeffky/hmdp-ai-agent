package com.hmdp.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 秒杀库存预热：应用启动时把上架中的秒杀券 MySQL 库存同步到 Redis。
 * 避免应用重启后 `seckill:stock:{id}` 缺失导致 Lua 脚本比较 nil 报错。
 * （xxl-job 的 seckillStockWarmup 任务做周期性兜底，本类做启动兜底）
 * 优化：批量 IN 查秒杀券 + MSET 批量写 Redis，只预热未过期券，避免 N+1 慢启动。
 */
@Component
public class SeckillStockWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockWarmer.class);

    @Resource
    private IVoucherService voucherService;

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

    /** 预热上架中且未过期的秒杀券库存到 Redis（批量），返回预热数量 */
    public int warmUp() {
        List<Voucher> seckillVouchers = voucherService.query()
                .eq("type", 1)
                .eq("status", 1)
                .list();
        if (seckillVouchers.isEmpty()) return 0;

        // 1 次批量查秒杀券（只取未过期），避免逐个 getById 的 N+1
        List<Long> ids = seckillVouchers.stream().map(Voucher::getId).collect(Collectors.toList());
        List<SeckillVoucher> activeSvs = seckillVoucherService.list(new QueryWrapper<SeckillVoucher>()
                .in("voucher_id", ids)
                .gt("end_time", LocalDateTime.now()));

        // 1 次 MSET 批量写 Redis
        Map<String, String> stockMap = new HashMap<>();
        for (SeckillVoucher sv : activeSvs) {
            if (sv.getStock() != null) {
                stockMap.put(SECKILL_STOCK_KEY + sv.getVoucherId(), String.valueOf(sv.getStock()));
            }
        }
        if (!stockMap.isEmpty()) {
            stringRedisTemplate.opsForValue().multiSet(stockMap);
        }
        return stockMap.size();
    }
}
