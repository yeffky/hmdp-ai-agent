-- ============================================================
-- agent_llm_trace Agent LLM 调用级 trace 表
-- 运行: 在 hmdp 库执行本文件（幂等）
-- 记录每次 LLM 调用的阶段/prompt/响应/token/耗时，供 AgentTrace 时间线回放
-- ============================================================

CREATE TABLE IF NOT EXISTS `agent_llm_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '请求 traceId（X-Trace-Id）',
  `stage` varchar(32) DEFAULT NULL COMMENT '阶段：agent/planner/structured/other',
  `prompt` text COMMENT '请求消息摘要（截断）',
  `response` text COMMENT '响应文本 / 工具调用摘要（截断）',
  `input_tokens` int DEFAULT NULL COMMENT '输入 token 数',
  `output_tokens` int DEFAULT NULL COMMENT '输出 token 数',
  `duration_ms` int DEFAULT NULL COMMENT '耗时(ms)',
  `model` varchar(64) DEFAULT NULL COMMENT '模型名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_llm_trace_id` (`trace_id`),
  KEY `idx_llm_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent LLM 调用级 trace';
