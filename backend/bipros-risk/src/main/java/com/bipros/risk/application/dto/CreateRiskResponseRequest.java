package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskResponseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRiskResponseRequest {
    @NotNull(message = "Response type is required")
    private RiskResponseType responseType;

    @NotBlank(message = "Description is required")
    private String description;

    private UUID responsibleId;
    private LocalDate plannedDate;
    private LocalDate actualDate;
    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
    @lombok.Builder.Default
    private String status = "PLANNED";
}
