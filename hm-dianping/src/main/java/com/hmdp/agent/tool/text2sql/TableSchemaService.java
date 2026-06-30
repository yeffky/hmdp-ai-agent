package com.hmdp.agent.tool.text2sql;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 表选择 + Schema 检索服务 —— Text2SQL 第一环节（渐进式披露）。
 *
 * <p>流程：
 * <ol>
 *   <li>从 Redis 获取表名列表（缓存），miss 时查 information_schema 回填</li>
 *   <li>LLM 根据用户查询选择相关表</li>
 *   <li>查 information_schema.COLUMNS 获取选中表的列结构</li>
 * </ol>
 */
public class TableSchemaService {

    private static final Logger log = LoggerFactory.getLogger(TableSchemaService.class);

    private static final String REDIS_KEY_TABLE_NAMES = "hmdp:table_names";
    private static final long TABLE_NAMES_TTL_HOURS = 24;

    private final OpenAiChatModel model;
    private final StringRedisTemplate redis;
    private final JdbcTemplate mysql;

    public TableSchemaService(OpenAiChatModel model, StringRedisTemplate redis, JdbcTemplate mysql) {
        this.model = model;
        this.redis = redis;
        this.mysql = mysql;
    }

    // ======== 公开 API ========

    /** 获取数据库中所有表名（从 Redis 缓存或 information_schema） */
    public Set<String> getAllTableNames() {
        return getTableList().stream().map(t -> t.name).collect(Collectors.toSet());
    }

    /**
     * 根据用户查询选择相关表并返回其完整列结构。
     *
     * @param userQuery 用户自然语言查询
     * @return key=表名, value=列定义列表，按表分组
     */
    public Map<String, List<ColDef>> selectAndFetchSchema(String userQuery) {
        // 1. 获取表名列表（Redis 缓存 → DB 兜底）
        List<TableSummary> allTables = getTableList();

        // 2. LLM 选择相关表
        List<String> selected = selectRelevantTables(userQuery, allTables);
        log.info("TableSelector: query='{}' → tables={}", userQuery, selected);

        // 3. 查 information_schema 获取列结构（仅选中表）
        Map<String, List<ColDef>> schema = new LinkedHashMap<>();
        for (String tableName : selected) {
            List<ColDef> cols = fetchColumns(tableName);
            if (!cols.isEmpty()) {
                schema.put(tableName, cols);
            }
        }
        return schema;
    }

    // ======== 第一步：获取表名列表 ========

    private List<TableSummary> getTableList() {
        // 尝试 Redis
        String cached = redis.opsForValue().get(REDIS_KEY_TABLE_NAMES);
        if (cached != null && !cached.isEmpty()) {
            return parseTableSummaries(cached);
        }
        // 查 DB
        List<TableSummary> tables = queryTableListFromDB();
        // 回填 Redis
        if (!tables.isEmpty()) {
            String value = tables.stream()
                    .map(t -> t.name + "|" + t.comment)
                    .collect(Collectors.joining("\n"));
            redis.opsForValue().set(REDIS_KEY_TABLE_NAMES, value, TABLE_NAMES_TTL_HOURS, TimeUnit.HOURS);
        }
        return tables;
    }

    private List<TableSummary> queryTableListFromDB() {
        try {
            return mysql.query(
                    "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = 'hmdp' AND TABLE_NAME LIKE 'tb_%' ORDER BY TABLE_NAME",
                    (rs, rowNum) -> new TableSummary(
                            rs.getString("TABLE_NAME"),
                            rs.getString("TABLE_COMMENT")
                    ));
        } catch (Exception e) {
            log.error("Failed to query information_schema.TABLES", e);
            return List.of();
        }
    }

    private List<TableSummary> parseTableSummaries(String raw) {
        List<TableSummary> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            int p = line.indexOf('|');
            if (p > 0) {
                list.add(new TableSummary(line.substring(0, p), line.substring(p + 1)));
            } else {
                list.add(new TableSummary(line, ""));
            }
        }
        return list;
    }

    // ======== 第二步：LLM 选表 ========

    private List<String> selectRelevantTables(String userQuery, List<TableSummary> allTables) {
        if (allTables.size() <= 2) {
            return allTables.stream().map(t -> t.name).collect(Collectors.toList());
        }

        String tableList = allTables.stream()
                .map(t -> t.name + " (" + t.comment + ")")
                .collect(Collectors.joining("\n"));

        String prompt = String.format("""
                用户查询: %s

                可选数据表:
                %s

                选出回答此查询所需的数据表（可以多选）。只输出表名，逗号分隔。""",
                userQuery, tableList);

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是数据库表选择器。只输出表名，逗号分隔。不要输出其他内容。"),
                    UserMessage.from(prompt)));
            String raw = resp.aiMessage().text().trim();

            List<String> selected = new ArrayList<>();
            for (String part : raw.split("[,\\s]+")) {
                String name = part.trim().replaceAll("[^a-zA-Z0-9_]", "");
                if (allTables.stream().anyMatch(t -> t.name.equalsIgnoreCase(name))
                        && selected.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                    selected.add(name);
                }
            }
            if (!selected.isEmpty()) return selected;
        } catch (Exception e) {
            log.error("TableSelector LLM failed", e);
        }
        // 兜底：返回全部表名
        return allTables.stream().map(t -> t.name).collect(Collectors.toList());
    }

    // ======== 第三步：查列结构 ========

    private List<ColDef> fetchColumns(String tableName) {
        try {
            return mysql.query(
                    "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY " +
                    "FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = 'hmdp' AND TABLE_NAME = ? " +
                    "ORDER BY ORDINAL_POSITION",
                    (rs, rowNum) -> new ColDef(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("DATA_TYPE"),
                            rs.getString("COLUMN_COMMENT"),
                            "PRI".equals(rs.getString("COLUMN_KEY")),
                            "YES".equals(rs.getString("IS_NULLABLE"))
                    ),
                    tableName
            );
        } catch (Exception e) {
            log.error("Failed to fetch columns for {}", tableName, e);
            return List.of();
        }
    }

    /** 格式化 schema 为 LLM prompt 文本 */
    public String formatSchema(Map<String, List<ColDef>> schema) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<ColDef>> e : schema.entrySet()) {
            sb.append("表 ").append(e.getKey()).append(":\n");
            for (ColDef col : e.getValue()) {
                sb.append("  ").append(col.name).append(" ").append(col.type);
                if (col.primaryKey) sb.append(" PK");
                if (!col.comment.isEmpty()) sb.append(" — ").append(col.comment);
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ======== 数据类 ========

    public static class TableSummary {
        public final String name;
        public final String comment;
        TableSummary(String name, String comment) {
            this.name = name;
            this.comment = comment == null ? "" : comment;
        }
    }

    public static class ColDef {
        public final String name;
        public final String type;
        public final String comment;
        public final boolean primaryKey;
        public final boolean nullable;
        ColDef(String name, String type, String comment, boolean primaryKey, boolean nullable) {
            this.name = name;
            this.type = type;
            this.comment = comment == null ? "" : comment;
            this.primaryKey = primaryKey;
            this.nullable = nullable;
        }
    }
}
