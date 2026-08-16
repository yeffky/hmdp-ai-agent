package com.hmdp.agent.tool.graph;

import cn.hutool.json.JSONUtil;
import com.hmdp.agent.ToolContext;
import com.hmdp.agent.tool.dto.SqlQueryResult;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text2SQL 工具 — LLM 动态生成 SQL 查询数据库。
 *
 * <p>内部三阶段编排（渐进式披露）：
 * <ol>
 *   <li><b>TableSchemaService</b>：Redis 取表名 → LLM 选表 → information_schema 查列结构</li>
 *   <li><b>SqlGenerator</b>：表结构 + 用户查询 → LLM 生成 SELECT → 多层安全校验</li>
 *   <li><b>JdbcTemplate</b>：执行校验通过的 SQL，返回结果</li>
 * </ol>
 *
 * <p><b>数据权限（P2 加固）</b>：
 * <ul>
 *   <li>表域白名单 {@link #QUERYABLE_TABLES}：只允许查询 C 端业务安全表，
 *       排除 tb_user 等隐私表；白名单外的新表默认拒绝（default-deny）。</li>
 *   <li>行级隔离 {@link #enforceRowLevel}：引用 tb_voucher_order 的 SQL 强制追加
 *       {@code user_id = 当前用户}，未登录拒绝——用户 A 无法查用户 B 的订单。</li>
 * </ul>
 */
@Component
public class Text2SqlTool {

    private static final Logger log = LoggerFactory.getLogger(Text2SqlTool.class);

    /**
     * Text2SQL 可查询业务表白名单（表域白名单）—— 排除用户隐私表（tb_user 含手机号/密码哈希等），
     * 以及任何未来新增的非业务表（默认拒绝）。需要用户归属隔离的表见 {@link #USER_SCOPED_TABLES}。
     */
    static final Set<String> QUERYABLE_TABLES = Set.of(
            "tb_shop", "tb_shop_type", "tb_shop_comment",
            "tb_city", "tb_district",
            "tb_voucher", "tb_seckill_voucher",
            "tb_voucher_order",
            "tb_blog", "tb_blog_comments");

    /** 需要行级用户隔离的表：生成 SQL 后强制追加 user_id = 当前用户，未登录拒绝。 */
    static final Set<String> USER_SCOPED_TABLES = Set.of("tb_voucher_order");

    /** 别名捕获时排除的 SQL 保留字（FROM tb_voucher_order JOIN ... 时避免把 JOIN 当别名）。 */
    private static final Set<String> SQL_RESERVED_ALIASES = Set.of(
            "JOIN", "WHERE", "ORDER", "LIMIT", "GROUP", "HAVING",
            "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "ON", "UNION",
            "OFFSET", "AS", "SET", "BY");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DataSource dataSource;

    @Resource
    private dev.langchain4j.model.chat.ChatModel model;

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
          "可查询商家、优惠券、订单、探店笔记等信息。适用于 complex 类复杂查询。")
    public SqlQueryResult query(
            @P("自然语言查询描述，将用户需求原样传入即可。例如'查询评分最高的10家火锅店'") String description) {

        Long userId = getCurrentUserId();

        // 注入用户定位坐标（持久化在 ToolContext，由 Agent 执行工具时设置），供生成 SQL 计算距离/附近用
        String location = ToolContext.getLocation();
        String queryDesc = description;
        if (location != null && !location.isEmpty()) {
            queryDesc = description + "\n\n【用户当前定位坐标】" + location
                    + "\n若查询涉及距离/附近，请以该坐标为用户所在位置计算（可用 ST_Distance_Sphere(POINT(x, y), POINT(经度, 纬度)) 或 Haversine 公式）。";
        }

        try {
            // ======== 第1步：选表 + 查列结构（表域白名单过滤） ========
            TableSchemaService ss = getSchemaService();
            Set<String> allTables = ss.getAllTableNames();
            // 表域白名单：只允许查询业务安全表（排除 tb_user 等隐私表），白名单外新表默认拒绝
            Set<String> allowedTables = new LinkedHashSet<>(allTables);
            allowedTables.retainAll(QUERYABLE_TABLES);
            Map<String, List<TableSchemaService.ColDef>> schema = ss.selectAndFetchSchema(queryDesc, allowedTables);

            if (schema.isEmpty()) {
                throw new com.hmdp.agent.graph.error.ToolException("text2Sql", "未找到相关数据表，无法查询");
            }

            log.info("Text2SQL step1: selected tables={}, allowed={}", schema.keySet(), allowedTables);

            // ======== 第2步：LLM 生成 SQL + 安全校验（白名单表） ========
            SqlGenerator sg = getSqlGenerator();
            String schemaText = ss.formatSchema(schema);
            String sql;
            try {
                sql = sg.generate(schemaText, queryDesc, userId, allowedTables);
            } catch (SqlGenerator.SqlRejectedException e) {
                log.warn("SQL rejected: {}", e.getMessage());
                throw new com.hmdp.agent.graph.error.ToolException("text2Sql", "SQL生成被安全策略拒绝: " + e.getMessage());
            }

            log.info("Text2SQL step2: SQL={}", sql);

            // ======== 第3步：SQL 安全加固 + 执行（含失败自修复循环，最多 3 次） ========
            String currentSql = enforceLimit(sql, 20);
            List<Map<String, Object>> rows = null;
            for (int attempt = 0; attempt <= 2; attempt++) {
                try {
                    rows = jdbcTemplate.queryForList(currentSql);
                    break;
                } catch (Exception e) {
                    if (attempt == 2) {
                        log.error("SQL execution failed after {} attempts: {} — {}",
                                attempt + 1, currentSql, e.getMessage());
                        throw new com.hmdp.agent.graph.error.ToolException("text2Sql",
                                "查询执行失败: " + e.getMessage(), e);
                    }
                    log.warn("SQL exec failed (attempt {}), asking LLM to fix: {} — {}",
                            attempt + 1, currentSql, e.getMessage());
                    try {
                        currentSql = getSqlGenerator().fixSql(
                                schemaText, queryDesc, userId, allowedTables, currentSql, e.getMessage());
                        currentSql = enforceLimit(currentSql, 20);
                    } catch (SqlGenerator.SqlRejectedException re) {
                        log.warn("SQL fix rejected: {}", re.getMessage());
                        throw new com.hmdp.agent.graph.error.ToolException("text2Sql",
                                "SQL修复被安全策略拒绝: " + re.getMessage(), re);
                    }
                }
            }

            // 行级隔离（最终统一加固，幂等）：订单类查询强制 user_id = 当前用户，未登录拒绝
            currentSql = enforceRowLevel(currentSql, userId);

            // 先 COUNT 总数（基于最终执行的 SQL）
            int totalCount = -1;
            String countSql = buildCountSql(currentSql);
            try {
                Number cnt = jdbcTemplate.queryForObject(countSql, Integer.class);
                if (cnt != null) totalCount = cnt.intValue();
            } catch (Exception ignored) {
                // COUNT 失败不影响主查询
            }

            log.info("Text2SQL step3: total={}, returned={}, sql={}", totalCount, rows.size(), currentSql);
            SqlQueryResult r = new SqlQueryResult();
            r.setRows(rows);
            r.setTotal(Math.max(totalCount, rows.size()));
            r.setHasMore(rows.size() >= 20 && totalCount > rows.size());
            return r;

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

    /**
     * 行级用户隔离（幂等）：SQL 引用用户归属表（tb_voucher_order）时，
     * 强制注入 {@code <qualifier>.user_id = 当前用户} 到 WHERE 开头；未登录直接拒绝。
     * 已含相同 guard 的 SQL 不再重复注入。
     *
     * @throws com.hmdp.agent.graph.error.ToolException 未登录查询订单数据时
     */
    static String enforceRowLevel(String sql, Long userId) throws com.hmdp.agent.graph.error.ToolException {
        if (sql == null || sql.isBlank()) return sql;
        if (USER_SCOPED_TABLES.stream().noneMatch(t -> containsIgnoreCase(sql, t))) {
            return sql;
        }
        if (userId == null || userId <= 0) {
            throw new com.hmdp.agent.graph.error.ToolException("text2Sql", "查询订单数据需要登录后使用");
        }
        String qualifier = extractAlias(sql, "tb_voucher_order");
        String guard = qualifier + ".user_id = " + userId;
        if (containsIgnoreCase(sql, guard)) {
            return sql; // 已加固（幂等，避免自修复循环重复注入）
        }
        int whereIdx = indexOfIgnoreCase(sql, " WHERE ");
        if (whereIdx >= 0) {
            // 插在 WHERE 关键字后：WHERE {guard} AND 原条件（AND 收紧，即使 LLM 已写 user_id 也只会更严）
            int insertAt = whereIdx + " WHERE ".length();
            return sql.substring(0, insertAt) + guard + " AND " + sql.substring(insertAt);
        }
        // 无 WHERE：插到 ORDER BY / LIMIT / GROUP BY 等之前（若存在），否则直接追加
        int cut = Integer.MAX_VALUE;
        for (String kw : new String[]{" ORDER BY ", " LIMIT ", " OFFSET ", " GROUP BY ", " HAVING "}) {
            int i = indexOfIgnoreCase(sql, kw);
            if (i >= 0) cut = Math.min(cut, i);
        }
        if (cut == Integer.MAX_VALUE) {
            return sql + " WHERE " + guard;
        }
        return sql.substring(0, cut) + " WHERE " + guard + " " + sql.substring(cut).trim();
    }

    /** 提取 FROM/JOIN 中指定表的别名（排除 SQL 保留字）；未别名返回表名本身。 */
    static String extractAlias(String sql, String table) {
        Matcher m = Pattern.compile(
                "\\b(?:FROM|JOIN)\\s+" + table + "\\s+(?:AS\\s+)?([A-Za-z_][A-Za-z0-9_]*)",
                Pattern.CASE_INSENSITIVE).matcher(sql);
        if (m.find() && !SQL_RESERVED_ALIASES.contains(m.group(1).toUpperCase(Locale.ROOT))) {
            return m.group(1);
        }
        return table;
    }

    private static boolean containsIgnoreCase(String s, String sub) {
        return s.toLowerCase(Locale.ROOT).contains(sub.toLowerCase(Locale.ROOT));
    }

    private static int indexOfIgnoreCase(String s, String sub) {
        return s.toLowerCase(Locale.ROOT).indexOf(sub.toLowerCase(Locale.ROOT));
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
