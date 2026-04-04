package com.bipros.integration.dto;

import com.bipros.integration.model.IntegrationLog;
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
public class IntegrationLogDto {

    private UUID id;

    private UUID integrationConfigId;

    private IntegrationLog.Direction direction;

    private String endpoint;

    private String requestPayload;

    private String responsePayload;

    private Integer httpStatus;

    private IntegrationLog.LogStatus status;

    private String errorMessage;

    private Long durationMs;

    private Instant createdAt;

    public static IntegrationLogDto from(IntegrationLog log) {
        return IntegrationLogDto.builder()
            .id(log.getId())
            .integrationConfigId(log.getIntegrationConfig().getId())
            .direction(log.getDirection())
            .endpoint(log.getEndpoint())
            .requestPayload(log.getRequestPayload())
            .responsePayload(log.getResponsePayload())
            .httpStatus(log.getHttpStatus())
            .status(log.getStatus())
            .errorMessage(log.getErrorMessage())
            .durationMs(log.getDurationMs())
            .createdAt(log.getCreatedAt())
            .build();
    }
}
