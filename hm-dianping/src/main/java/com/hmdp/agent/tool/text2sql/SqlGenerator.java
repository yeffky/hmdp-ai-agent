package com.hmdp.agent.tool.text2sql;

import dev.langchain4j.model.openai.OpenAiChatModel;
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

    private final OpenAiChatModel model;

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

    public SqlGenerator(OpenAiChatModel model) {
        this.model = model;
    }

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
        sb.append("""

                ## 规则
                - 只生成一条 SELECT 语句
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
