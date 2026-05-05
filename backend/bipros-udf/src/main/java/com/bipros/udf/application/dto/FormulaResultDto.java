package com.bipros.udf.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormulaResultDto {
    private String formulaCode;
    private String expressionUsed;
    private BigDecimal value;
    private String formatted;
    private boolean error;
    private String errorMessage;
}
