package com.bipros.dbs.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Single-day DBS payload for one Construction Manager (sum across their supervisors).
 * The {@code *Lines} fields are the CM's downline supervisor rows' section lines merged at
 * read time (grouped by description + unit + rate, qty and amount summed — the same
 * aggregation the supervisor period view uses), so the CM accordions render real rows
 * instead of "No lines recorded". {@code boqLines} is the merged flat "BOQ Work executed"
 * list; the direct/prelim split stays totals-only (stored lines carry no split flag).
 *
 * <p>Phase 4: {@code directCost}, {@code prelimCost}, {@code totalCostInclPrelims} and
 * {@code pctAchieved} are present on the wire but populated to zero until Phase 7 wires
 * the prelim split into the supervisor calculators.
 *
 * <p>{@code contributionPct} is a FRACTION (0.9823 = 98.23%), consistent with the supervisor,
 * engineer and project payloads. {@code totalIncome}, {@code totalExpense} and
 * {@code contribution} were previously computed in the aggregator and discarded for want of
 * columns, which is why the CM tab showed no income and no cost.
 */
public record DbsCmDayResponse(
    UUID id,
    UUID projectId,
    UUID cmUserId,
    LocalDate reportDate,
    List<UUID> siteManagerIds,
    List<UUID> engineerIds,
    Integer supervisorCount,
    BigDecimal materialAmount,
    BigDecimal manpowerAmount,
    BigDecimal adminAmount,
    BigDecimal machineryAmount,
    BigDecimal fuelAmount,
    BigDecimal subcontractAmount,
    BigDecimal directCost,
    BigDecimal prelimCost,
    BigDecimal totalCostInclPrelims,
    BigDecimal boqForTheDayAmount,
    BigDecimal boqPlannedToDate,
    BigDecimal boqAchievedToDate,
    BigDecimal totalExpense,
    BigDecimal totalIncome,
    BigDecimal contribution,
    BigDecimal contributionPct,
    BigDecimal pctAchieved,
    List<DbsSectionLineDto> materialLines,
    List<DbsSectionLineDto> manpowerLines,
    List<DbsSectionLineDto> adminLines,
    List<DbsSectionLineDto> machineryLines,
    List<DbsSectionLineDto> fuelLines,
    List<DbsSectionLineDto> boqLines,
    Instant recomputedAt
) {}
