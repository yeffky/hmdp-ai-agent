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

            // ======== 第3步：执行 SQL ========
            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(sql);
            } catch (Exception e) {
                log.error("SQL execution failed: {} — {}", sql, e.getMessage());
                return "查询执行失败: " + e.getMessage();
            }

            if (rows.isEmpty()) {
                return "[]";
            }

            // 限制返回行数，避免 LLM 上下文爆炸
            int maxRows = 30;
            if (rows.size() > maxRows) {
                rows = rows.subList(0, maxRows);
            }

            log.info("Text2SQL step3: {} rows returned (capped at {})", rows.size(), maxRows);
            return JSONUtil.toJsonPrettyStr(rows);

        } catch (Exception e) {
            log.error("Text2SQL failed for query: {}", description, e);
            return "Text2SQL查询异常: " + e.getMessage();
        }
    }

    private Long getCurrentUserId() {
        try {
            if (UserHolder.getUser() != null) return UserHolder.getUser().getId();
        } catch (Exception ignored) {}
        return null;
    }
}
