-- ============================================================
-- tb_voucher 加 image 字段（团购商品封面图）
-- 运行: 在 hmdp 库执行本文件（幂等）
-- ============================================================

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_voucher' AND COLUMN_NAME = 'image');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_voucher` ADD COLUMN `image` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''团购商品封面图'' AFTER `sub_title`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;