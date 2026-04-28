package com.bipros.analytics.infrastructure.clickhouse;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Wires the secondary ClickHouse DataSources alongside the primary Postgres DataSource Spring Boot
 * auto-configures. Two pools are exposed: {@code clickhouseDataSource} for the writer (ETL
 * insert + admin DDL) and {@code clickhouseReaderDataSource} for read-only query traffic from the
 * Phase-2 analytics tool layer.
 *
 * <p>Both beans are declared with {@code defaultCandidate = false} (Spring 6.2+). This is critical:
 * Spring Boot's {@code DataSourceAutoConfiguration} uses {@code @ConditionalOnMissingBean(DataSource.class)}
 * to decide whether to create the Postgres Hikari pool. Without {@code defaultCandidate = false}
 * those two ClickHouse beans match that check and Spring Boot SKIPS creating the Postgres
 * DataSource — which cascades into a missing {@code entityManagerFactory} and breaks JPA
 * (UserRepository → CustomUserDetailsService → JwtAuthenticationFilter all fail at boot).
 *
 * <p>{@code defaultCandidate = false} keeps the beans injectable via {@code @Qualifier("clickhouseDataSource")}
 * (which is how every consumer in this module already wires them), but excludes them from
 * autowire-by-type resolution and from the {@code @ConditionalOnMissingBean} check.
 */
@Configuration
@EnableConfigurationProperties(ClickHouseProperties.class)
public class ClickHouseDataSourceConfig {

    private static final String DRIVER = "com.clickhouse.jdbc.ClickHouseDriver";

    @Bean(name = "clickhouseDataSource", destroyMethod = "close", defaultCandidate = false)
    public DataSource clickhouseDataSource(ClickHouseProperties props) {
        return buildPool(props.url(), props.username(), props.password(), "ch-writer", 8);
    }

    @Bean(name = "clickhouseJdbcTemplate")
    public JdbcTemplate clickhouseJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("clickhouseDataSource") DataSource ds) {
        JdbcTemplate t = new JdbcTemplate(ds);
        t.setFetchSize(5_000);
        return t;
    }

    @Bean(name = "clickhouseReaderDataSource", destroyMethod = "close", defaultCandidate = false)
    public DataSource clickhouseReaderDataSource(ClickHouseProperties props) {
        return buildPool(props.url(), props.readerUsername(), props.readerPassword(), "ch-reader", 4);
    }

    @Bean(name = "clickhouseReaderJdbcTemplate")
    public JdbcTemplate clickhouseReaderJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("clickhouseReaderDataSource") DataSource ds) {
        JdbcTemplate t = new JdbcTemplate(ds);
        t.setFetchSize(5_000);
        return t;
    }

    private static HikariDataSource buildPool(String url, String user, String pass, String name, int max) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setDriverClassName(DRIVER);
        cfg.setMaximumPoolSize(max);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(10_000);
        cfg.setPoolName(name);
        cfg.addDataSourceProperty("socket_timeout", "60000");
        cfg.addDataSourceProperty("compress", "true");
        return new HikariDataSource(cfg);
    }
}
