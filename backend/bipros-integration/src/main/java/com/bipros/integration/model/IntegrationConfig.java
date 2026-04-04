package com.bipros.integration.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "integration_configs", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationConfig extends BaseEntity {

    @Column(name = "system_code", unique = true, nullable = false, length = 50)
    private String systemCode;

    @Column(name = "system_name", nullable = false, length = 255)
    private String systemName;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = false;

    @Column(name = "auth_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuthType authType = AuthType.NONE;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private IntegrationStatus status = IntegrationStatus.INACTIVE;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    public enum AuthType {
        NONE,
        API_KEY,
        OAUTH2,
        JWT
    }

    public enum IntegrationStatus {
        ACTIVE,
        INACTIVE,
        ERROR
    }
}
