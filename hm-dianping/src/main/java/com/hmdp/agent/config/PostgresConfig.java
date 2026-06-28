package com.hmdp.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据源配置 — 供 Checkpoint Saver 和 UserStore 共用。
 *
 * 仅在 agent.postgres.enabled=true 时激活。
 * PG 不可达时启动不会失败，回退到内存存储。
 */
@Configuration
@ConditionalOnProperty(name = "agent.postgres.enabled", havingValue = "true")
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
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);

        // 验证连接（失败只 warn，不阻止启动）
        try (java.sql.Connection conn = ds.getConnection()) {
            log.info("PostgreSQL connected: {}", url);
        } catch (Exception e) {
            log.warn("PostgreSQL unreachable at {}: {} — app will start but graph uses in-memory storage",
                    url, e.getMessage());
        }
        return ds;
    }

    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(DataSource postgresDataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        initUserProfileTable(jdbc);
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
}
