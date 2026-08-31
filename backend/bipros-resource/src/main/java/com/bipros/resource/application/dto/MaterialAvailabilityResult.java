package com.bipros.resource.application.dto;

import java.util.List;

/**
 * Availability report payload. {@code tracked} is false when the project has no store data at all
 * (no GRNs, no issue slips, no storekeeper log rows) — consumers must then show a
 * "stock not tracked" state instead of zero balances, which would be fiction.
 */
public record MaterialAvailabilityResult(
    boolean tracked,
    List<MaterialBalanceRow> rows
) {}
