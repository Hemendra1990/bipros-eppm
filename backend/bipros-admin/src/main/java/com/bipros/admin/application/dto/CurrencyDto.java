package com.bipros.admin.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyDto {
    private UUID id;
    private String code;
    private String name;
    private String symbol;
    private BigDecimal exchangeRate;
    private Boolean isBaseCurrency;
    private Integer decimalPlaces;
}
