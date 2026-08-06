package com.hmdp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 MySQL 连接池真正绑定了 application.yaml 的 spring.datasource.hikari.*。
 * 若不绑定，Hikari 走默认值（connectionTimeout=30000、maxLifetime=30min），
 * 远程 MySQL 空闲断连后池内连接失效，出现 "No operations allowed after connection closed"。
 */
class MybatisConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.driver-class-name=com.mysql.jdbc.Driver",
                    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hmdp",
                    "spring.datasource.username=root",
                    "spring.datasource.password=x",
                    "spring.datasource.hikari.maximum-pool-size=4",
                    "spring.datasource.hikari.minimum-idle=1",
                    "spring.datasource.hikari.idle-timeout=300000",
                    "spring.datasource.hikari.max-lifetime=600000",
                    "spring.datasource.hikari.connection-timeout=10000",
                    "spring.datasource.hikari.validation-timeout=5000",
                    "spring.datasource.hikari.connection-test-query=SELECT 1"
            )
            .withUserConfiguration(MybatisConfig.class);

    @Test
    void mysqlDataSource应绑定hikari连接池参数而非默认值() {
        runner.run(context -> {
            DataSource ds = context.getBean("dataSource", DataSource.class);
            assertThat(ds).isInstanceOf(HikariDataSource.class);

            HikariDataSource hikari = (HikariDataSource) ds;
            assertThat(hikari.getConnectionTimeout()).isEqualTo(10_000L);
            assertThat(hikari.getMaxLifetime()).isEqualTo(600_000L);
            assertThat(hikari.getIdleTimeout()).isEqualTo(300_000L);
            assertThat(hikari.getMaximumPoolSize()).isEqualTo(4);
            assertThat(hikari.getMinimumIdle()).isEqualTo(1);
            assertThat(hikari.getConnectionTestQuery()).isEqualTo("SELECT 1");
        });
    }
}
