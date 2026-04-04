package com.bipros.admin.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCurrencyRequest {
    @NotBlank(message = "Code is required")
    @Size(min = 3, max = 3, message = "Code must be exactly 3 characters")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private String symbol;
    private BigDecimal exchangeRate;
    @Default
    private Boolean isBaseCurrency = false;
    @Default
    private Integer decimalPlaces = 2;
}
