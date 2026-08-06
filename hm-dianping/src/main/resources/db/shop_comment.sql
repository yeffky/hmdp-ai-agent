-- ============================================================
-- tb_shop_comment 店铺评论表
-- 运行: 在 hmdp 库执行本文件（幂等）
-- ============================================================

CREATE TABLE IF NOT EXISTS `tb_shop_comment` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '商铺id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '评论用户id',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '评论内容',
  `rating` tinyint(1) UNSIGNED NOT NULL DEFAULT 5 COMMENT '评分 1-5',
  `liked` int(8) UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态 0正常/1举报/2禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_shop` (`shop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '店铺评论';
