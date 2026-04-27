package com.bipros.risk.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A single cell in the Probability × Impact scoring matrix.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskScoringMatrixDto {
    private UUID id;
    private UUID projectId;
    @NotNull
    @Min(1) @Max(5)
    private Integer probabilityValue;
    @NotNull
    @Min(1) @Max(5)
    private Integer impactValue;
    @NotNull
    @Min(0)
    private Integer score;
}
