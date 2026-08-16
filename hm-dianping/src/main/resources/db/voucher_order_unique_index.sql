-- ============================================================
-- tb_voucher_order 加唯一索引 uk_user_voucher（秒杀幂等兜底）
-- 防止同一用户重复购买同一券（Redis 数据丢失 / 锁失效后的 DB 最终防线）
-- 运行: 在 hmdp 库执行本文件（幂等）
-- ============================================================

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_voucher_order' AND INDEX_NAME = 'uk_user_voucher');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `tb_voucher_order` ADD UNIQUE INDEX `uk_user_voucher` (`user_id`, `voucher_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
