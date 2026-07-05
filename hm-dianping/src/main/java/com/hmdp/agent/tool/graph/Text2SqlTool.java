package com.hmdp.agent.tool.graph;

import cn.hutool.json.JSONUtil;
import com.hmdp.agent.tool.text2sql.SqlGenerator;
import com.hmdp.agent.tool.text2sql.TableSchemaService;
import com.hmdp.utils.UserHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.*;

/**
 * Text2SQL 工具 — LLM 动态生成 SQL 查询数据库。
 *
 * <p>内部三阶段编排（渐进式披露）：
 * <ol>
 *   <li><b>TableSchemaService</b>：Redis 取表名 → LLM 选表 → information_schema 查列结构</li>
 *   <li><b>SqlGenerator</b>：表结构 + 用户查询 → LLM 生成 SELECT → 多层安全校验</li>
 *   <li><b>JdbcTemplate</b>：执行校验通过的 SQL，返回结果</li>
 * </ol>
 */
@Component
public class Text2SqlTool {

    private static final Logger log = LoggerFactory.getLogger(Text2SqlTool.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DataSource dataSource;

    @Resource
    private dev.langchain4j.model.openai.OpenAiChatModel model;

    private JdbcTemplate jdbcTemplate;
    private volatile TableSchemaService schemaService;
    private volatile SqlGenerator sqlGenerator;

    @PostConstruct
    void init() {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** 每2小时清除表名缓存，下次查询时从 MySQL 懒加载重建 */
    @Scheduled(fixedRate = 2 * 60 * 60 * 1000, initialDelay = 2 * 60 * 60 * 1000)
    public void scheduledEvictTableCache() {
        try {
            getSchemaService().evictTableNameCache();
        } catch (Exception e) {
            log.warn("Scheduled table cache eviction failed: {}", e.getMessage());
        }
    }

    private TableSchemaService getSchemaService() {
        if (schemaService == null) {
            synchronized (this) {
                if (schemaService == null) {
                    schemaService = new TableSchemaService(model, stringRedisTemplate, jdbcTemplate);
                }
            }
        }
        return schemaService;
    }

    private SqlGenerator getSqlGenerator() {
        if (sqlGenerator == null) {
            synchronized (this) {
                if (sqlGenerator == null) {
                    sqlGenerator = new SqlGenerator(model);
                }
            }
        }
        return sqlGenerator;
    }

    @Tool("根据自然语言动态生成SQL查询数据库。支持灵活组合查询条件、跨表JOIN、聚合统计等。" +
          "可查询商家、优惠券、订单、用户、探店笔记等信息。适用于 complex 类复杂查询。")
    public String query(
            @P("自然语言查询描述，将用户需求原样传入即可。例如'查询评分最高的10家火锅店'") String description) {

        Long userId = getCurrentUserId();

        try {
            // ======== 第1步：选表 + 查列结构 ========
            TableSchemaService ss = getSchemaService();
            Set<String> allTables = ss.getAllTableNames(); // 全部表名，用于校验白名单
            Map<String, List<TableSchemaService.ColDef>> schema = ss.selectAndFetchSchema(description);

            if (schema.isEmpty()) {
                return "未找到相关数据表，无法查询。";
            }

            log.info("Text2SQL step1: selected tables={}, allowed={}", schema.keySet(), allTables);

            // ======== 第2步：LLM 生成 SQL + 安全校验（允许查全部表）=======
            SqlGenerator sg = getSqlGenerator();
            String schemaText = ss.formatSchema(schema);
            String sql;
            try {
                sql = sg.generate(schemaText, description, userId, allTables);
            } catch (SqlGenerator.SqlRejectedException e) {
                log.warn("SQL rejected: {}", e.getMessage());
                return "SQL生成被安全策略拒绝: " + e.getMessage();
            }

            log.info("Text2SQL step2: SQL={}", sql);

            // ======== 第3步：SQL 安全加固 + 执行 ========
            sql = enforceLimit(sql, 20);

            // 先 COUNT 总数
            int totalCount = -1;
            String countSql = buildCountSql(sql);
            try {
                Number cnt = jdbcTemplate.queryForObject(countSql, Integer.class);
                if (cnt != null) totalCount = cnt.intValue();
            } catch (Exception ignored) {
                // COUNT 失败不影响主查询
            }

            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(sql);
            } catch (Exception e) {
                log.error("SQL execution failed: {} — {}", sql, e.getMessage());
                throw new com.hmdp.agent.graph.error.ToolException("text2Sql",
                        "查询执行失败: " + e.getMessage(), e);
            }

            String resultJson;
            if (rows.isEmpty()) {
                resultJson = "[]";
            } else {
                resultJson = JSONUtil.toJsonPrettyStr(rows);
            }

            // 结构化汇总信息
            StringBuilder summary = new StringBuilder();
            if (totalCount >= 0) {
                summary.append("【总记录数】").append(totalCount).append(" 条");
                if (totalCount > 20) {
                    summary.append(" → 仅展示前 20 条，请缩小查询条件（如加 WHERE、选更具体的表）");
                }
                summary.append("\n");
            }
            if (rows.isEmpty()) {
                summary.append("【结果】未查到数据\n");
            }

            log.info("Text2SQL step3: total={}, returned={}, sql={}", totalCount, rows.size(), sql);
            return summary + "【SQL】" + sql + "\n【结果】" + resultJson;

        } catch (com.hmdp.agent.graph.error.ToolException e) {
            throw e;  // 直接透传，避免重复包装
        } catch (Exception e) {
            log.error("Text2SQL failed for query: {}", description, e);
            throw new com.hmdp.agent.graph.error.ToolException("text2Sql",
                    "Text2SQL查询异常: " + e.getMessage(), e);
        }
    }

    private Long getCurrentUserId() {
        Long ctxUserId = com.hmdp.agent.ToolContext.getUserId();
        if (ctxUserId != null && ctxUserId > 0) return ctxUserId;
        try {
            if (UserHolder.getUser() != null) return UserHolder.getUser().getId();
        } catch (Exception ignored) {}
        return null;
    }

    // ======== SQL 安全加固 ========

    /** 强制追加 LIMIT，防止全表扫描 */
    static String enforceLimit(String sql, int defaultLimit) {
        String upper = sql.trim().toUpperCase();
        if (upper.contains(" LIMIT ") || upper.contains(" TOP ") || upper.contains(" FETCH ")) {
            return sql;
        }
        String clean = sql.trim().replaceAll(";+\\s*$", "");
        return clean + " LIMIT " + defaultLimit;
    }

    /** 从 SELECT 构建 COUNT 查询，去掉 ORDER BY/LIMIT/OFFSET */
    static String buildCountSql(String sql) {
        String upper = sql.trim().toUpperCase();
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) return "SELECT 0";
        String afterFrom = sql.substring(fromIdx);
        afterFrom = afterFrom.replaceAll("(?i)\\s+ORDER\\s+BY\\s+.*", "");
        afterFrom = afterFrom.replaceAll("(?i)\\s+LIMIT\\s+\\d+.*", "");
        afterFrom = afterFrom.replaceAll("(?i)\\s+OFFSET\\s+\\d+.*", "");
        return "SELECT COUNT(*)" + afterFrom;
    }
}
