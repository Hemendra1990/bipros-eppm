package com.bipros.project.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One approved DPR material consumption line, projected for the Material Consumption Report.
 * {@code activityId} comes from the parent DPR; {@code materialName} is free-text (DPR material
 * has no material-master FK). All monetary/quantity fields may be null.
 */
public record DprMaterialLine(
    LocalDate reportDate,
    UUID activityId,
    String materialName,
    String unit,
    BigDecimal quantity,
    BigDecimal unitRate,
    BigDecimal lineCost) {}
