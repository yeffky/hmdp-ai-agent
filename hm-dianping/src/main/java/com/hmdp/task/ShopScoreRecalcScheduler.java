package com.hmdp.task;

import com.hmdp.mapper.ShopMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 店铺评分延迟重算：每 10 分钟按用户评论均分批量刷新一次店铺评分。
 * 发评论只累加评论数，评分在这里批量重算，避免每条评论触发全表 AVG 扫描。
 */
@Component
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "false", matchIfMissing = true)
public class ShopScoreRecalcScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShopScoreRecalcScheduler.class);

    @Resource
    private ShopMapper shopMapper;

    @Scheduled(fixedDelay = 600000) // 每 10 分钟
    public void recalcShopScores() {
        try {
            int n = shopMapper.recalcScores();
            log.info("店铺评分延迟重算完成，更新 {} 家", n);
        } catch (Exception e) {
            log.error("店铺评分延迟重算失败", e);
        }
    }
}
