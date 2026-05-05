package com.bipros.udf.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormulaOverrideDto {
    private UUID id;
    private String formulaCode;
    private UUID projectId;
    private String overrideExpression;
    private Boolean isActive;
    private String effectiveFrom;
    private String effectiveTo;
    private String overrideReason;
    private Long overrideVersion;
}
