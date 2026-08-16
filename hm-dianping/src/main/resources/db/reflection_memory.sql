-- ============================================================
-- agent_reflection Agent 跨会话失败教训记忆表（Reflexion）
-- 运行: 在 hmdp 库执行本文件（幂等）
-- ============================================================

CREATE TABLE IF NOT EXISTS `agent_reflection` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` varchar(64) DEFAULT NULL COMMENT '产生该教训的会话 threadId',
  `domain` varchar(32) NOT NULL COMMENT '领域标签：搜店/团购/订单/排队/通用',
  `user_query` varchar(255) NOT NULL COMMENT '触发失败的用户查询',
  `lesson` varchar(1000) NOT NULL COMMENT '教训：失败原因 + 正确做法',
  `keywords` varchar(255) DEFAULT NULL COMMENT '检索关键词（逗号分隔，供读路径匹配）',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_reflection_keywords` (`keywords`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Agent 跨会话失败教训记忆（Reflexion）';
