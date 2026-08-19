-- ============================================================
-- tb_voucher 有效秒杀券预热索引（幂等）
-- 用于 JOIN 查询：v.type = 1 AND v.status = 1 AND v.id = sv.voucher_id
-- ============================================================

SET @has_idx := (SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tb_voucher'
    AND INDEX_NAME = 'idx_type_status_id');

SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `tb_voucher` ADD INDEX `idx_type_status_id` (`type`, `status`, `id`)',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
