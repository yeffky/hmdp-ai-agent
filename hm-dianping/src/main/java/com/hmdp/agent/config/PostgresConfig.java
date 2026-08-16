package com.hmdp.agent.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据源配置 — 供 Checkpoint Saver、UserStore 和 ChatHistoryRepository 共用。
 *
 * <p>使用 HikariCP 连接池替代 {@code PGSimpleDataSource}，
 * 避免每次 checkpoint/getConnection 操作都新建 TCP 连接。</p>
 */
@Configuration
public class PostgresConfig {

    private static final Logger log = LoggerFactory.getLogger(PostgresConfig.class);

    @Value("${agent.postgres.url:jdbc:postgresql://localhost:5432/hmdp}")
    private String url;

    @Value("${agent.postgres.username:postgres}")
    private String username;

    @Value("${agent.postgres.password:postgres}")
    private String password;

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        // 连接池调优：checkpoint saver + UserStore + ChatHistory 共用，池太小并发易耗尽
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(8000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        // keepalive：定期对空闲连接发保活查询，防止远程 PG/中间网络设备静默回收空闲连接
        //（曾出现 "This connection has been closed" → 死连接占满池 → checkpoint 读取超时无响应）
        config.setKeepaliveTime(120000);
        config.setValidationTimeout(5000);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("PostgresPool");

        HikariDataSource ds = new HikariDataSource(config);

        try (java.sql.Connection conn = ds.getConnection()) {
            log.info("PostgreSQL connected (HikariCP): {}", url);
        } catch (Exception e) {
            throw new RuntimeException("PostgreSQL unreachable at " + url + ": " + e.getMessage(), e);
        }
        return ds;
    }

    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource postgresDataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        initUserProfileTable(jdbc);
        initChatHistoryTable(jdbc);
        return jdbc;
    }

    private void initUserProfileTable(JdbcTemplate jdbc) {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS tb_user_profile (" +
                "  user_id BIGINT PRIMARY KEY," +
                "  profile_json JSONB NOT NULL DEFAULT '{}'," +
                "  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  total_sessions INT DEFAULT 0" +
                ")"
            );
            log.info("tb_user_profile table ready");
        } catch (Exception e) {
            log.error("Failed to init tb_user_profile: {}", e.getMessage());
        }
    }

    private void initChatHistoryTable(JdbcTemplate jdbc) {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS tb_chat_history (" +
                "  id BIGSERIAL PRIMARY KEY," +
                "  user_id BIGINT NOT NULL," +
                "  user_message TEXT NOT NULL," +
                "  assistant_message TEXT," +
                "  cards JSONB," +
                "  blocks JSONB," +
                "  create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            // 已有库升级：补齐卡片列与回答结构列（幂等）
            jdbc.execute(
                "ALTER TABLE tb_chat_history ADD COLUMN IF NOT EXISTS cards JSONB"
            );
            jdbc.execute(
                "ALTER TABLE tb_chat_history ADD COLUMN IF NOT EXISTS blocks JSONB"
            );
            jdbc.execute(
                "CREATE INDEX IF NOT EXISTS idx_chat_history_user_id " +
                "ON tb_chat_history(user_id, id DESC)"
            );
            log.info("tb_chat_history table ready");
        } catch (Exception e) {
            log.error("Failed to init tb_chat_history: {}", e.getMessage());
        }
    }
}
