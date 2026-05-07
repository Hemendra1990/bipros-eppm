package com.bipros.resource.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Leaner DTO used by the DPR drawer's resource picker. Returns only the fields the searchable
 * dropdown needs (assignment id, resource label, unit, snapshotted rate / basis, planned vs
 * actual units) plus the resource-type kind so the UI can split the response by tab.
 */
public record AssignedResourcePickerOption(
    UUID assignmentId,
    UUID resourceId,
    String resourceName,
    String resourceCode,
    /** Resource.unit verbatim — e.g. "Day", "Hour", "MT". UI uses for display. */
    String unit,
    /** Coarse rate basis (HOUR / DAY / EACH) — drives the cost formula on the client preview. */
    String unitRateBasis,
    /** Snapshot of the unit rate for the requested {@code reportDate}; null when no rate. */
    BigDecimal unitRate,
    String rateType,
    Double plannedUnits,
    Double actualUnits,
    BigDecimal plannedCost,
    BigDecimal actualCost,
    /** MANPOWER / EQUIPMENT / MATERIAL — copied from the resource type code (LABOR maps to MANPOWER). */
    String kind) {}
