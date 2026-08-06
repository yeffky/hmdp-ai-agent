-- ============================================================
-- tb_shop 加 queue_enabled 字段（部分商家可关闭排队功能）
-- 运行: 在 hmdp 库执行本文件（幂等）
-- ============================================================

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'queue_enabled');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `queue_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''是否支持排队取号：1=支持，0=不支持'' AFTER `open_hours`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 默认所有既有商家支持排队（保持兼容）
UPDATE `tb_shop` SET `queue_enabled` = 1 WHERE `queue_enabled` IS NULL;