package com.bipros.integration.dto;

import com.bipros.integration.model.IntegrationConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationConfigDto {

    private UUID id;

    @NotBlank(message = "System code is required")
    private String systemCode;

    @NotBlank(message = "System name is required")
    private String systemName;

    @NotBlank(message = "Base URL is required")
    private String baseUrl;

    private String apiKey;

    @NotNull(message = "Enabled status is required")
    private Boolean isEnabled;

    @NotNull(message = "Auth type is required")
    private IntegrationConfig.AuthType authType;

    private Instant lastSyncAt;

    @NotNull(message = "Status is required")
    private IntegrationConfig.IntegrationStatus status;

    private String configJson;

    private Instant createdAt;

    private Instant updatedAt;

    public static IntegrationConfigDto from(IntegrationConfig config) {
        return IntegrationConfigDto.builder()
            .id(config.getId())
            .systemCode(config.getSystemCode())
            .systemName(config.getSystemName())
            .baseUrl(config.getBaseUrl())
            .apiKey(config.getApiKey())
            .isEnabled(config.getIsEnabled())
            .authType(config.getAuthType())
            .lastSyncAt(config.getLastSyncAt())
            .status(config.getStatus())
            .configJson(config.getConfigJson())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }

    public IntegrationConfig toEntity() {
        IntegrationConfig config = new IntegrationConfig();
        config.setSystemCode(systemCode);
        config.setSystemName(systemName);
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        config.setIsEnabled(isEnabled);
        config.setAuthType(authType);
        config.setLastSyncAt(lastSyncAt);
        config.setStatus(status);
        config.setConfigJson(configJson);
        return config;
    }
}
