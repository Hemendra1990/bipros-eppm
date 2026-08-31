package com.bipros.importexport.application.dto;

import java.util.List;

/**
 * Outcome of resolving Task-1's canonical resource tables (MANPOWER/EQUIPMENT/MATERIAL/
 * SUBCONTRACTOR) onto the app's role+variant model. {@code *Rows} is the row count found in the
 * parsed file per table; {@code *Applied} is how many of those rows resolved and were created
 * (or, for {@link com.bipros.importexport.application.service.RoleResourcePlanApplier#preview},
 * would resolve). Unresolved rows never throw — they add an entry to {@code warnings} instead.
 */
public record ResourceApplyResult(
    int manpowerRows,
    int manpowerApplied,
    int equipmentRows,
    int equipmentApplied,
    int materialRows,
    int materialApplied,
    int subContractorRows,
    int subContractorApplied,
    List<String> warnings) {}
