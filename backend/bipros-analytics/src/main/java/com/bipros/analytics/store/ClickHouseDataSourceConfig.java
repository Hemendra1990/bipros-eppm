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
        log.info("ClickHouse datasource configured: url={}", properties.getUrl());
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate clickHouseNamedParameterJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        return new NamedParameterJdbcTemplate(clickHouseDataSource);
    }
}
