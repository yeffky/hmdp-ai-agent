package com.hmdp.agent.tool.graph;

import com.hmdp.agent.graph.error.ToolException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text2SQL 安全加固：行级用户隔离（enforceRowLevel）、LIMIT 兜底、COUNT 构造、别名提取。
 */
class Text2SqlToolTest {

    // ======== enforceRowLevel：行级用户隔离 ========

    @Test
    void orderQuery_withAlias_injectsGuardedWhere() throws Exception {
        String sql = "SELECT o.id, v.title FROM tb_voucher_order o JOIN tb_voucher v ON o.voucher_id = v.id " +
                "WHERE o.status = 1 ORDER BY o.create_time DESC LIMIT 20";
        String guarded = Text2SqlTool.enforceRowLevel(sql, 1001L);
        assertTrue(guarded.contains("WHERE o.user_id = 1001 AND o.status = 1"), guarded);
    }

    @Test
    void orderQuery_withoutAlias_usesTableNameQualifier() throws Exception {
        String sql = "SELECT id FROM tb_voucher_order WHERE status = 1";
        String guarded = Text2SqlTool.enforceRowLevel(sql, 1001L);
        assertTrue(guarded.contains("WHERE tb_voucher_order.user_id = 1001 AND status = 1"), guarded);
    }

    @Test
    void orderQuery_noWhere_insertsBeforeOrderBy() throws Exception {
        String sql = "SELECT id FROM tb_voucher_order ORDER BY id";
        String guarded = Text2SqlTool.enforceRowLevel(sql, 1001L);
        assertTrue(guarded.contains("WHERE tb_voucher_order.user_id = 1001 ORDER BY id"), guarded);
    }

    @Test
    void nonScopedTable_unchanged() throws Exception {
        String sql = "SELECT id, name FROM tb_shop WHERE score >= 80 LIMIT 20";
        assertEquals(sql, Text2SqlTool.enforceRowLevel(sql, 1001L));
    }

    @Test
    void orderQuery_withoutLogin_rejected() {
        String sql = "SELECT id FROM tb_voucher_order WHERE status = 1";
        ToolException e = assertThrows(ToolException.class,
                () -> Text2SqlTool.enforceRowLevel(sql, null));
        assertTrue(e.getMessage().contains("登录"), e.getMessage());
    }

    @Test
    void alreadyGuarded_idempotent() throws Exception {
        // 无别名 SQL，guard 形式为 tb_voucher_order.user_id = 1001 → 已存在则不再重复注入
        String sql = "SELECT id FROM tb_voucher_order WHERE tb_voucher_order.user_id = 1001 AND status = 1";
        String guarded = Text2SqlTool.enforceRowLevel(sql, 1001L);
        assertEquals(sql, guarded);
    }

    // ======== extractAlias：别名提取（排除保留字） ========

    @Test
    void extractAlias_findsAlias() {
        assertEquals("o", Text2SqlTool.extractAlias("FROM tb_voucher_order o WHERE", "tb_voucher_order"));
        assertEquals("o", Text2SqlTool.extractAlias("FROM tb_voucher_order AS o WHERE", "tb_voucher_order"));
    }

    @Test
    void extractAlias_joinKeywordNotTreatedAsAlias() {
        // FROM tb_voucher_order JOIN ... → JOIN 是保留字，不应被当别名
        assertEquals("tb_voucher_order",
                Text2SqlTool.extractAlias("FROM tb_voucher_order JOIN tb_voucher v", "tb_voucher_order"));
    }

    // ======== enforceLimit：防全表扫描 ========

    @Test
    void enforceLimit_appendsWhenMissing() {
        String sql = Text2SqlTool.enforceLimit("SELECT id FROM tb_shop", 20);
        assertTrue(sql.endsWith("LIMIT 20"), sql);
    }

    @Test
    void enforceLimit_keepsExistingLimit() {
        String sql = Text2SqlTool.enforceLimit("SELECT id FROM tb_shop LIMIT 10", 20);
        assertTrue(sql.contains("LIMIT 10"), sql);
    }

    // ======== buildCountSql：COUNT 构造 ========

    @Test
    void buildCountSql_stripsOrderAndLimit() {
        String count = Text2SqlTool.buildCountSql(
                "SELECT id, name FROM tb_shop WHERE score >= 80 ORDER BY score DESC LIMIT 20");
        assertEquals("SELECT COUNT(*) FROM tb_shop WHERE score >= 80", count);
    }
}
