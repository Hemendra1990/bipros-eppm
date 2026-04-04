package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskResponseType;
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
public class RiskResponseDto {
    private UUID id;
    private UUID riskId;
    private RiskResponseType responseType;
    private String description;
    private UUID responsibleId;
    private LocalDate plannedDate;
    private LocalDate actualDate;
    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
    private String status;
}
