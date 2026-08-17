package com.bipros.analytics.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ClickHouseDataSourceConfig {

    private final ClickHouseProperties properties;

    @Bean(defaultCandidate = false)
    public DataSource clickHouseDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(properties.getPool().getMaxSize());
        config.setMinimumIdle(properties.getPool().getMinIdle());
        config.setConnectionTimeout(properties.getPool().getConnectionTimeout());
        config.setPoolName("ClickHousePool");
        config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        // -1: skip the initial connection check so boot survives when ClickHouse is down or
        // absent (e.g. local dev without Docker — ClickHouse has no native Windows build).
        // First actual use still fails per connection-timeout; the ETL listeners already
        // catch + dead-letter those failures, so nothing else needs to change.
        config.setInitializationFailTimeout(-1);
        log.info("ClickHouse datasource configured: url={}", properties.getUrl());
        return new HikariDataSource(config);
    }

    // defaultCandidate = false: this template must be opted-into by name (e.g. via @Qualifier or
    // a constructor parameter named clickHouseJdbcTemplate). Without this, an unqualified
    // `JdbcTemplate` autowire site sees two ambiguous candidates (the Spring-Boot auto-configured
    // Postgres one + this one) and silently picks the wrong target — sending Postgres-only SQL
    // (e.g. `pg_constraint`, `information_schema.columns`) to ClickHouse, which fails with
    // "Unknown table expression identifier". Keep the matching flag on the named-parameter
    // variant for symmetry.
    @Bean(defaultCandidate = false)
    public JdbcTemplate clickHouseJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }

    @Bean(defaultCandidate = false)
    public NamedParameterJdbcTemplate clickHouseNamedParameterJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        return new NamedParameterJdbcTemplate(clickHouseDataSource);
    }
}
