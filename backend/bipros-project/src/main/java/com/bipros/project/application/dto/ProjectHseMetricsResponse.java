package com.bipros.project.application.dto;

import com.bipros.project.domain.model.ProjectHseMetrics;

import java.math.BigDecimal;

/** Read/echo shape for the per-project HSE inputs. */
public record ProjectHseMetricsResponse(
    BigDecimal kmDistanceDriven,
    BigDecimal indirectManHours
) {
    public static ProjectHseMetricsResponse from(ProjectHseMetrics e) {
        return new ProjectHseMetricsResponse(
            e.getKmDistanceDriven(),
            e.getIndirectManHours() != null ? e.getIndirectManHours() : BigDecimal.ZERO);
    }
}
