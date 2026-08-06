package com.hmdp.task;

import com.hmdp.service.IVoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 秒杀券到期自动下架（本地 @Scheduled 兜底）。
 * end_time 已过的秒杀券置 status=3 过期，使其在数据层不可再秒杀/购买。
 * 幂等 UPDATE，每 10 分钟跑一次；部署到服务器后由 xxl-job 的 seckillVoucherExpire 接管。
 */
@Component
public class SeckillVoucherExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeckillVoucherExpireScheduler.class);

    @Resource
    private IVoucherService voucherService;

    @Scheduled(fixedDelay = 600000) // 每 10 分钟
    public void expireSeckillVouchers() {
        try {
            voucherService.update()
                    .set("status", 3)
                    .eq("type", 1)
                    .eq("status", 1)
                    .apply("id IN (SELECT voucher_id FROM tb_seckill_voucher WHERE end_time < NOW())")
                    .update();
            log.info("秒杀券到期下架扫描完成");
        } catch (Exception e) {
            log.error("秒杀券到期下架扫描失败", e);
        }
    }
}
