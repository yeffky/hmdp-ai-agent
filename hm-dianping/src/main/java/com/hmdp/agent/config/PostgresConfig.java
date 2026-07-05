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
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
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
                "  create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP" +
                ")"
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
