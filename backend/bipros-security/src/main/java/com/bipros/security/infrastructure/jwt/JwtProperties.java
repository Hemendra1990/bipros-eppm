package com.bipros.security.infrastructure.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bipros.security.jwt")
public class JwtProperties {

    private String secret;

    private long accessTokenExpiration = 3600000; // 1 hour in milliseconds

    private long refreshTokenExpiration = 86400000; // 24 hours in milliseconds
}
