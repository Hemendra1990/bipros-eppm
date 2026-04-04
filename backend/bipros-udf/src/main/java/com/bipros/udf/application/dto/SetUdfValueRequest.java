package com.bipros.udf.application.dto;

import com.bipros.udf.domain.model.IndicatorColor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetUdfValueRequest {
    private String textValue;
    private Double numberValue;
    private BigDecimal costValue;
    private LocalDate dateValue;
    private IndicatorColor indicatorValue;
    private String codeValue;
}
