package com.bipros.admin.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobServiceDto {
    private UUID id;
    private String name;
    private UUID projectId;
    private String status;
    private Instant lastRunAt;
    private Instant nextRunAt;
    private String cronExpression;
    private Long lastDurationMs;
    private String lastError;
}
