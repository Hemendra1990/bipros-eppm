package com.bipros.project.application.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Upsert body for the HSE inputs. Both fields optional; each defaults to 0 when null.
 */
public record UpdateProjectHseMetricsRequest(
    @PositiveOrZero BigDecimal kmDistanceDriven,
    @PositiveOrZero BigDecimal indirectManHours
) {}
