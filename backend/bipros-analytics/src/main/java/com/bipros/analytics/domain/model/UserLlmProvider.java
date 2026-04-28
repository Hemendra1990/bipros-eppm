package com.bipros.analytics.domain.model;

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
import java.util.UUID;

@Entity
@Table(name = "user_llm_providers", schema = "analytics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLlmProvider extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LlmProvider provider;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "encrypted_api_key")
    private byte[] encryptedApiKey;

    @Column(name = "endpoint_override", length = 512)
    private String endpointOverride;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProviderStatus status = ProviderStatus.ACTIVE;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_validation_error", length = 512)
    private String lastValidationError;
}
