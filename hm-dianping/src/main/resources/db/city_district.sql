-- ============================================================
-- 城市/地区 迁移（支持双城市地图与 Agent 按地区查询）
-- 运行: 在 hmdp 库执行本文件（幂等：可重复执行）
-- 坐标系统: GCJ-02（与高德地图/现有种子数据一致）
-- ============================================================

-- 1. 城市表
CREATE TABLE IF NOT EXISTS `tb_city` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '城市名，如 杭州/福州',
  `sort` int(3) UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT='城市表';

-- 2. 地区表（含地图 GEO 圆心，供距离排序/地图初始化使用）
CREATE TABLE IF NOT EXISTS `tb_district` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `city_id` bigint(20) UNSIGNED NOT NULL COMMENT '所属城市 id（关联 tb_city.id）',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '地区名，如 拱墅区/鼓楼区',
  `center_x` double NOT NULL COMMENT '地区中心经度（GCJ-02）',
  `center_y` double NOT NULL COMMENT '地区中心纬度（GCJ-02）',
  `sort` int(3) UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_city` (`city_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT='地区表（含地图 GEO 圆心坐标）';

-- 3. tb_shop 关联地区
SET @has_district_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND COLUMN_NAME = 'district_id');
SET @ddl := IF(@has_district_col = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `district_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT ''所属地区 id（关联 tb_district.id）'' AFTER `type_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_shop' AND INDEX_NAME = 'idx_district_type');
SET @ddl2 := IF(@has_idx = 0,
  'ALTER TABLE `tb_shop` ADD INDEX `idx_district_type`(`district_id`, `type_id`)',
  'SELECT 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 4. 种子数据：杭州 + 福州
INSERT INTO `tb_city` (`id`, `name`, `sort`) VALUES (1, '杭州', 1), (2, '福州', 2)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `tb_district` (`id`, `city_id`, `name`, `center_x`, `center_y`, `sort`) VALUES
  (1, 1, '拱墅区', 120.147, 30.325, 1),
  (2, 2, '鼓楼区', 119.3026, 26.0855, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 5. 既有店铺（原杭州拱墅区数据）归属到拱墅区
UPDATE `tb_shop` SET `district_id` = 1 WHERE `district_id` IS NULL AND `x` BETWEEN 120.0 AND 120.3 AND `y` BETWEEN 30.2 AND 30.5;
