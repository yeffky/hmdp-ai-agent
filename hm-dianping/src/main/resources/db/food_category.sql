-- ============================================================
-- tb_shop 加 美食细分 + 描述 + 服务字段 + 索引（幂等）
-- 运行: 在 hmdp 库执行本文件（幂等）
-- ============================================================

-- food_category 美食细分
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'food_category');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `food_category` varchar(32) DEFAULT NULL COMMENT ''美食细分：奶茶咖啡/快餐小吃/火锅/烧烤烤肉/地方菜系/异域料理/自助餐/海鲜/面包蛋糕/食品生鲜'' AFTER `type_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- description 菜品/环境/服务描述
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'description');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `description` varchar(1000) DEFAULT NULL COMMENT ''菜品/环境/服务描述（规则生成）'' AFTER `x`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- has_parking 停车
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'has_parking');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `has_parking` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否支持停车'' AFTER `description`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- child_friendly 儿童友好
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'child_friendly');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `child_friendly` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否儿童友好'' AFTER `has_parking`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pet_friendly 宠物友好
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'pet_friendly');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `pet_friendly` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否宠物友好'' AFTER `child_friendly`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- max_seats 最多容纳
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'max_seats');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `max_seats` int(11) NOT NULL DEFAULT 4 COMMENT ''最多容纳（人）'' AFTER `pet_friendly`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 美食细分筛选索引
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND INDEX_NAME = 'idx_food_category');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `tb_shop` ADD INDEX `idx_food_category` (`food_category`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
