package com.bipros.analytics.infrastructure.clickhouse;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bipros.analytics.clickhouse")
public record ClickHouseProperties(
        String url,
        String username,
        String password,
        String readerUsername,
        String readerPassword
) {
}
