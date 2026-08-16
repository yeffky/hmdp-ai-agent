package com.hmdp.agent.tool.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * text2Sql 结构化输出（工具输出 schema）。
 * 避免把 SQL 语句与结果混在自由文本中；SQL 仅用于日志审计，不进入结构化结果。
 */
@Data
public class SqlQueryResult {

    /** 查询结果行（已强制 LIMIT） */
    private List<Map<String, Object>> rows;

    /** 总记录数（COUNT 预检；-1 表示预检失败） */
    private long total;

    /** 是否还有更多（结果被 LIMIT 截断） */
    private boolean hasMore;
}
