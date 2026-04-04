package com.bipros.udf.application.dto;

import com.bipros.udf.domain.model.IndicatorColor;
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
public class UdfValueResponse {
    private UUID id;
    private UUID userDefinedFieldId;
    private UUID entityId;
    private String textValue;
    private Double numberValue;
    private BigDecimal costValue;
    private LocalDate dateValue;
    private IndicatorColor indicatorValue;
    private String codeValue;
}
