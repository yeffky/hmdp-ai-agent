package com.hmdp.agent.config;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据源配置 — 供 Checkpoint Saver 和 UserStore 共用。
 *
 * <p>使用 {@link PGSimpleDataSource} 直连，绕过 JDBC {@code DriverManager}
 * 避免 classpath 上的 MySQL 驱动错误接管 PostgreSQL 连接。</p>
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
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(url);
        ds.setUser(username);
        ds.setPassword(password);

        try (java.sql.Connection conn = ds.getConnection()) {
            log.info("PostgreSQL connected: {}", url);
        } catch (Exception e) {
            throw new RuntimeException("PostgreSQL unreachable at " + url + ": " + e.getMessage(), e);
        }
        return ds;
    }

    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource postgresDataSource) {
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
