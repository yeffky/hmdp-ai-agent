package com.hmdp.agent.tool.text2sql;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * SQL 生成 + 安全校验 —— Text2SQL 第二环节。
 *
 * <p>LLM 根据表结构 + 用户需求生成 SELECT SQL，然后经过多层安全校验：
 * <ol>
 *   <li>必须是 SELECT 语句</li>
 *   <li>禁止 DML/DDL 关键词（INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE）</li>
 *   <li>禁止多语句（分号注入）</li>
 *   <li>禁止危险函数（如 INTO OUTFILE、LOAD_FILE 等）</li>
 *   <li>表名白名单：只能查询选中的表</li>
 *   <li>禁止查询敏感列（password 等）</li>
 * </ol>
 */
public class SqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerator.class);

    private final ChatModel model;

    // 危险关键词 — 包含即拒绝（用 \b 单词边界避免误匹配 create_time 等列名）
    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|RENAME|" +
            "GRANT|REVOKE|EXEC|EXECUTE|CALL|MERGE)\\b",
            Pattern.CASE_INSENSITIVE);

    // 危险函数/模式
    private static final Pattern DANGEROUS_FUNC = Pattern.compile(
            "\\b(LOAD_FILE|INTO\\s+(OUTFILE|DUMPFILE)|BENCHMARK|GET_LOCK|RELEASE_LOCK)\\b",
            Pattern.CASE_INSENSITIVE);

    // 多语句检测
    private static final Pattern MULTI_STMT = Pattern.compile(";.*\\S");

    // SELECT 开头（允许 EXPLAIN / WITH 前缀）
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*(SELECT|WITH|EXPLAIN)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public SqlGenerator(ChatModel model) {
        this.model = model;
    }

    /**
     * few-shot 示例库 — 各业务场景的典型查询模板，帮助 LLM 生成高质量 SQL。
     * 示例中的字面值（店ID/用户ID/地区ID）为占位示意，LLM 需按实际查询替换。
     */
    private static final String FEW_SHOTS = """
            ## 参考示例（示例数字为占位，按实际查询替换）
            Q: 评分最高的10家火锅店
            A: SELECT id, name, avg_price, score, comments FROM tb_shop WHERE type_id = 1 AND food_category = '火锅' ORDER BY score DESC, comments DESC LIMIT 10;

            Q: 搜名字带"海底捞"的店
            A: SELECT id, name, area, avg_price, score FROM tb_shop WHERE name LIKE '%海底捞%' LIMIT 20;

            Q: 拱墅区能带宠物的餐厅
            A: SELECT id, name, food_category, avg_price, pet_friendly FROM tb_shop WHERE type_id = 1 AND district_id = 1 AND pet_friendly = 1 LIMIT 20;

            Q: 某家店的团购套餐
            A: SELECT id, title, sub_title, pay_value, actual_value, type FROM tb_voucher WHERE shop_id = 5 AND status = 1 LIMIT 20;

            Q: 某家店的网友评价（最近几条）
            A: SELECT content, rating, create_time FROM tb_shop_comment WHERE shop_id = 5 ORDER BY create_time DESC LIMIT 5;

            Q: 某用户待支付的订单
            A: SELECT o.id, v.title, o.pay_type, o.status FROM tb_voucher_order o JOIN tb_voucher v ON o.voucher_id = v.id WHERE o.user_id = 1001 AND o.status = 1 ORDER BY o.create_time DESC LIMIT 20;
            """;

    /**
     * 生成并校验 SQL。
     *
     * @param schema    表结构文本（来自 TableSchemaService.formatSchema）
     * @param userQuery 用户自然语言查询
     * @param userId    当前用户 ID（可能为 null），供 LLM 在需要时嵌入
     * @param allowedTables 本次查询允许的表名集合
     * @return 校验通过的 SQL 语句
     * @throws SqlRejectedException 安全校验不通过
     */
    public String generate(String schema, String userQuery, Long userId, Set<String> allowedTables)
            throws SqlRejectedException {

        String prompt = buildPrompt(schema, userQuery, userId);

        String sql;
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是SQL生成器。只输出一行纯SQL，不要markdown代码块，不要解释。"),
                    UserMessage.from(prompt)));
            sql = cleanSql(resp.aiMessage().text());
            log.info("SqlGenerator raw: {}", sql);
        } catch (Exception e) {
            throw new SqlRejectedException("LLM调用失败: " + e.getMessage());
        }

        validate(sql, allowedTables);
        return sql;
    }

    /**
     * 执行失败自修复：把失败的 SQL + 数据库报错回喂 LLM，让其修正后重新校验。
     *
     * @param failedSql 执行失败的 SQL
     * @param error     数据库返回的错误信息
     */
    public String fixSql(String schema, String userQuery, Long userId, Set<String> allowedTables,
                         String failedSql, String error) throws SqlRejectedException {
        StringBuilder sb = new StringBuilder();
        sb.append("## 数据库表结构\n").append(schema).append("\n");
        sb.append("## 用户查询\n").append(userQuery).append("\n");
        if (userId != null) {
            sb.append("\n当前用户ID: ").append(userId).append("（查询用户自身数据时请用此ID）");
        }
        sb.append("\n## 之前生成的 SQL（执行失败）\n").append(failedSql).append("\n");
        sb.append("\n## 执行报错\n").append(truncate(error, 500)).append("\n");
        sb.append("""

                ## 修复要求
                - 根据报错修正 SQL（常见原因：列名写错、表名写错、类型不匹配、缺少别名、引号问题、列不在 schema 中）
                - 只能使用上面表结构中列出的列名，禁止编造
                - 只输出修正后的单条 SELECT，不要解释""");

        String sql;
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是SQL修复器。根据错误修正SQL，只输出一行纯SQL，不要markdown代码块，不要解释。"),
                    UserMessage.from(sb.toString())));
            sql = cleanSql(resp.aiMessage().text());
            log.info("SqlGenerator fix raw: {}", sql);
        } catch (Exception e) {
            throw new SqlRejectedException("LLM修复失败: " + e.getMessage());
        }

        validate(sql, allowedTables);
        return sql;
    }

    private String buildPrompt(String schema, String userQuery, Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 数据库表结构\n");
        sb.append(schema);
        sb.append("\n## 用户查询\n");
        sb.append(userQuery);
        if (userId != null) {
            sb.append("\n\n当前用户ID: ").append(userId)
              .append("（查询用户自身数据时请用此ID）");
        }
        sb.append("\n\n").append(FEW_SHOTS);
        sb.append("""

                ## 规则
                - 只生成一条 SELECT 语句
                - 只能使用上面表结构中列出的列名，绝对禁止编造任何列名（如 avatar、phone、email、address 等常见名如果不在schema中就不能用）
                - 如果 JOIN 的表不在上面的表结构列表中，说明该表不需要 JOIN，去掉这个 JOIN
                - JOIN 时使用表名.列名格式避免歧义
                - 使用 MySQL 语法
                - 查询结果数量控制在合理范围，默认 LIMIT 20
                - 排序字段如果不是索引列，不要排
                - 禁止查询 password 等敏感字段
                - 禁止使用 SELECT *
                - 类型匹配：score 是 INT（1~5分×10），比较时注意除以10
                - 用户提及的店铺名往往不是全称，店铺名查询务必使用 LIKE '%关键词%' 模糊匹配""");
        return sb.toString();
    }

    private String cleanSql(String raw) {
        // 去掉 markdown 代码块
        String sql = raw.trim();
        if (sql.startsWith("```")) {
            int end = sql.indexOf('\n');
            if (end > 0) sql = sql.substring(end + 1);
            if (sql.endsWith("```")) sql = sql.substring(0, sql.length() - 3);
        }
        return sql.trim();
    }

    // ======== 安全校验 ========

    void validate(String sql, Set<String> allowedTables) throws SqlRejectedException {
        if (sql == null || sql.isBlank()) {
            throw new SqlRejectedException("SQL为空");
        }

        String upper = sql.toUpperCase();

        // 1. 必须以 SELECT/WITH 开头
        if (!SELECT_PATTERN.matcher(sql).matches()) {
            throw new SqlRejectedException("只允许SELECT查询，实际: " + truncate(sql, 80));
        }

        // 2. 禁止多语句
        if (MULTI_STMT.matcher(sql).find()) {
            throw new SqlRejectedException("禁止多语句查询");
        }

        // 3. 禁止危险关键词（\b 单词边界，避免误匹配 create_time 等列名）
        java.util.regex.Matcher kwMatcher = DANGEROUS_KEYWORDS.matcher(sql);
        if (kwMatcher.find()) {
            throw new SqlRejectedException("SQL包含禁止的关键词: " + kwMatcher.group());
        }

        // 4. 禁止危险函数
        if (DANGEROUS_FUNC.matcher(sql).find()) {
            throw new SqlRejectedException("SQL包含危险的函数调用");
        }

        // 5. 表名白名单校验：提取 FROM 和 JOIN 后的表名
        Set<String> referenced = extractTableNames(sql);
        for (String ref : referenced) {
            boolean allowed = false;
            for (String at : allowedTables) {
                if (at.equalsIgnoreCase(ref)) { allowed = true; break; }
            }
            if (!allowed) {
                throw new SqlRejectedException("SQL引用了未授权的表: " + ref + "，允许的表: " + allowedTables);
            }
        }

        // 6. 禁止查敏感列
        if (upper.matches(".*\\bPASSWORD\\b.*")) {
            throw new SqlRejectedException("禁止查询password等敏感字段");
        }
    }

    /** 从 SQL 中提取 FROM / JOIN 引用的表名（简单正则，覆盖大部分场景） */
    private Set<String> extractTableNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Pattern p = Pattern.compile(
                "\\b(?:FROM|JOIN)\\s+`?(\\w+)`?",
                Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(sql);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** SQL 被安全校验拒绝时抛出 */
    public static class SqlRejectedException extends Exception {
        public SqlRejectedException(String reason) {
            super(reason);
        }
    }
}
