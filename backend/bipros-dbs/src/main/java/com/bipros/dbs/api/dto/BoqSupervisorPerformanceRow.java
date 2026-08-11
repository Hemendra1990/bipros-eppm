package com.bipros.dbs.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One (BOQ item × supervisor) row of the "BOQ level performance supervisor wise — Cost"
 * comparison (AI Agent sheet, DBS row). Read-only aggregation of approved DPRs; no stored
 * values are touched.
 *
 * <p>Conventions mirror the DBS engine exactly:
 * <ul>
 *   <li>{@code qty}/{@code income} count only billable rows (measurement operations,
 *       pre-split legacy rows, QUANTITY_PARTITION children) minus the sub-contractor
 *       share — the same predicate SectionFBoqCalculator prices at supervisor scope;</li>
 *   <li>{@code manpowerCost}/{@code machineryCost}/{@code materialCost} use the Section
 *       A/C/E rate resolution (line_cost preferred, else nos × unit_rate, else the
 *       variant/master rate) over ALL of the supervisor's approved DPRs on the item —
 *       crews cost money on non-billable operation rows too;</li>
 *   <li>{@code fuelCost} = the live fuel ratio × machineryCost (Section D rule);</li>
 *   <li>Section B (admin/catering) is not attributable to a BOQ item and is excluded —
 *       a supervisor's Σcost across items reconciles to their DBS expense minus Section B.</li>
 * </ul>
 * {@code contributionPct} is a FRACTION (0.875 = 87.5%), like the other DBS payloads.
 * {@code supervisorUserId} is null for free-text supervisors (identified by name).
 */
public record BoqSupervisorPerformanceRow(
    String itemNo,
    String description,
    String unit,
    BigDecimal boqRate,
    UUID supervisorUserId,
    String supervisorName,
    BigDecimal qty,
    BigDecimal income,
    BigDecimal manpowerCost,
    BigDecimal machineryCost,
    BigDecimal fuelCost,
    BigDecimal materialCost,
    BigDecimal totalCost,
    BigDecimal contribution,
    BigDecimal contributionPct
) {}
