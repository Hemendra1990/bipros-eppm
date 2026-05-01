package com.bipros.ai.provider;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "ai", name = "llm_provider_config")
@Getter
@Setter
public class LlmProviderConfig extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key_ciphertext", nullable = false)
    private byte[] apiKeyCiphertext;

    @Column(name = "api_key_iv", nullable = false)
    private byte[] apiKeyIv;

    @Column(name = "api_key_version", nullable = false)
    private int apiKeyVersion = 1;

    @Column(nullable = false, length = 200)
    private String model;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 4096;

    @Column(nullable = false, precision = 3, scale = 2)
    private java.math.BigDecimal temperature = new java.math.BigDecimal("0.20");

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 120000;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_headers", columnDefinition = "jsonb")
    private String extraHeaders;

    @Column(name = "auth_scheme", nullable = false, length = 20)
    private String authScheme = "BEARER";

    @Column(name = "supports_native_tools", nullable = false)
    private boolean supportsNativeTools = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
