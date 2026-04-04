package com.bipros.baseline.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BaselineVarianceResponse(
    UUID activityId,
    String activityName,
    Long startVarianceDays,
    Long finishVarianceDays,
    Double durationVariance,
    BigDecimal costVariance) {}
