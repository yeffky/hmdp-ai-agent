package com.hmdp.task;

import com.hmdp.agent.tool.graph.Text2SqlTool;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.rag.document.DocumentFileWatcher;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * xxl-job 定时任务统一入口（由调度中心按 cron 触发）。
 * 迁移自 @Scheduled：店铺评分重算 / 订单超时取消 / Text2SQL缓存刷新 / RAG文档扫描，
 * 并新增：秒杀库存预热 / 秒杀券到期下架。
 */
@Component
public class XxlJobHandlers {

    private static final Logger log = LoggerFactory.getLogger(XxlJobHandlers.class);

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private SeckillStockWarmer seckillStockWarmer;

    @Resource
    private Text2SqlTool text2SqlTool;

    @Resource
    private DocumentFileWatcher documentFileWatcher;

    /** 店铺评分延迟重算（每 10 分钟） */
    @XxlJob("shopScoreRecalc")
    public void shopScoreRecalc() {
        int n = shopMapper.recalcScores();
        log.info("[xxl-job] 店铺评分重算完成，更新 {} 家", n);
    }

    /** 待支付订单超时取消（15 分钟未付 → 取消并回补秒杀库存） */
    @XxlJob("orderTimeoutCancel")
    public void orderTimeoutCancel() {
        voucherOrderService.cancelExpiredOrders();
        log.info("[xxl-job] 待支付订单超时扫描完成");
    }

    /** 秒杀库存预热：把上架中的秒杀券 MySQL 库存同步到 Redis（应用重启后兜底） */
    @XxlJob("seckillStockWarmup")
    public void seckillStockWarmup() {
        int n = seckillStockWarmer.warmUp();
        log.info("[xxl-job] 秒杀库存预热完成，预热 {} 个秒杀券", n);
    }

    /** 秒杀券到期自动下架（end_time 已过 → status=3 过期） */
    @XxlJob("seckillVoucherExpire")
    public void seckillVoucherExpire() {
        boolean ok = voucherService.update()
                .set("status", 3)
                .eq("type", 1)
                .eq("status", 1)
                .apply("id IN (SELECT voucher_id FROM tb_seckill_voucher WHERE end_time < NOW())")
                .update();
        log.info("[xxl-job] 秒杀券到期下架执行{}", ok ? "成功" : "完成");
    }

    /** Text2SQL 表名缓存刷新（每 2 小时） */
    @XxlJob("tableCacheRefresh")
    public void tableCacheRefresh() {
        text2SqlTool.scheduledEvictTableCache();
    }

    /** RAG 文档全量扫描（处理离线期间变更） */
    @XxlJob("ragDocFullScan")
    public void ragDocFullScan() {
        documentFileWatcher.fullScan();
    }
}
