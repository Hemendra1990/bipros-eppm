package com.bipros.project.application.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Upsert body for the HSE inputs. {@code kmDistanceDriven} optional; defaults to 0 when null.
 */
public record UpdateProjectHseMetricsRequest(
    @PositiveOrZero BigDecimal kmDistanceDriven
) {}
