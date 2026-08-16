-- ============================================================
-- agent_trace 表：Agent 会话执行轨迹（可观测性后台）
-- 运行: 在 hmdp 库执行本文件（幂等）
-- ============================================================

CREATE TABLE IF NOT EXISTS `agent_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `trace_id` varchar(64) NOT NULL COMMENT '请求 traceId（X-Trace-Id）',
  `user_id` bigint DEFAULT NULL COMMENT '用户id',
  `query` varchar(1000) DEFAULT NULL COMMENT '用户问题',
  `steps_json` json DEFAULT NULL COMMENT '节点执行步骤序列 [{node,tool,at}]',
  `node_count` int NOT NULL DEFAULT 0 COMMENT '节点数',
  `total_ms` bigint NOT NULL DEFAULT 0 COMMENT '图执行总耗时(ms)',
  `status` varchar(16) NOT NULL DEFAULT 'completed' COMMENT 'completed / error',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 执行轨迹（可观测性）';
