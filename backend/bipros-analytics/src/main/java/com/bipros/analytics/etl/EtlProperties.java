package com.bipros.analytics.etl;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bipros.analytics.etl")
public record EtlProperties(
        boolean enabled,
        Integer pageSize,
        String scheduleCron
) {
    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 5_000 : pageSize;
    }
}
