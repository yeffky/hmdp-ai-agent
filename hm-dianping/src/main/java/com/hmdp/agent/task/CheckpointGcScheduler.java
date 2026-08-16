package com.hmdp.agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checkpoint 线程 TTL 归档 —— 防止 PG 中 lg4jthread / lg4jcheckpoint 无限膨胀。
 *
 * <p>LangGraph checkpoint 按 {@code threadId}（user:{id}）累积，线程永不释放，
 * 用户越多表越大、resume 时回放行数越多。本任务每日执行：
 * <ol>
 *   <li><b>释放</b>：超过 {@code inactive-days} 无新 checkpoint 的线程置 {@code is_released=TRUE}
 *       （lg4jcheckpoint.saved_at 为活动时间；释放后 loadRawRows 不再读取，即归档）；</li>
 *   <li><b>物理清理</b>：已释放线程中超过 {@code purge-days} 的 checkpoint 行直接删除。</li>
 * </ol>
 *
 * <p>PG 不可用时仅告警不中断（checkpoint 写入不受影响）。
 */
@Component
public class CheckpointGcScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckpointGcScheduler.class);

    /** 线程无新 checkpoint 超过该天数 → 标记释放（归档） */
    @Value("${agent.checkpoint-gc.inactive-days:7}")
    private int inactiveDays;

    /** 已释放线程的 checkpoint 超过该天数 → 物理删除 */
    @Value("${agent.checkpoint-gc.purge-days:30}")
    private int purgeDays;

    private final JdbcTemplate pg;

    public CheckpointGcScheduler(@Qualifier("postgresJdbcTemplate") JdbcTemplate pg) {
        this.pg = pg;
    }

    @Scheduled(cron = "${agent.checkpoint-gc.cron:0 30 4 * * ?}")
    public void gc() {
        try {
            // 1. 释放：最新 checkpoint 早于 inactive-days 的线程（最近无活动）
            int released = pg.update("""
                    UPDATE lg4jthread t SET is_released = TRUE
                    WHERE t.is_released = FALSE
                      AND NOT EXISTS (
                          SELECT 1 FROM lg4jcheckpoint c
                          WHERE c.thread_id = t.thread_id
                            AND c.saved_at > NOW() - (INTERVAL '1 day' * ?)
                      )
                    """, inactiveDays);

            // 2. 清理：已释放线程中超过 purge-days 的 checkpoint 行
            int purged = pg.update("""
                    DELETE FROM lg4jcheckpoint c
                    WHERE c.thread_id IN (SELECT thread_id FROM lg4jthread WHERE is_released = TRUE)
                      AND c.saved_at < NOW() - (INTERVAL '1 day' * ?)
                    """, purgeDays);

            log.info("Checkpoint GC: released {} threads (inactive>={}d), purged {} checkpoints (>={}d)",
                    released, inactiveDays, purged, purgeDays);
        } catch (Exception e) {
            // PG 不可用 / 表未创建：checkpoint 写入走同一库，此时多半已在上游失败，这里只告警
            log.warn("Checkpoint GC skipped (PG unavailable or tables missing): {}", e.getMessage());
        }
    }
}
