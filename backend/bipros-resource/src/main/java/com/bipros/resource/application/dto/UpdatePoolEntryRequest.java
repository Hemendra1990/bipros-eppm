package com.bipros.resource.application.dto;

import java.math.BigDecimal;

public record UpdatePoolEntryRequest(
    BigDecimal rateOverride,
    Double availabilityOverride,
    String customUnit,
    String notes
) {}
