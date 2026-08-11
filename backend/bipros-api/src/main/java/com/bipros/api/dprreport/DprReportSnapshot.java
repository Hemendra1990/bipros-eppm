package com.bipros.api.dprreport;

import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.dbs.api.dto.DbsProjectDayResponse;
import com.bipros.project.application.dto.DailyProgressReportResponse;
import com.bipros.project.application.dto.DprIssueRow;
import com.bipros.project.application.dto.DailyCostReportResponse;
import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport;

import java.math.BigDecimal;
import java.util.List;

public record DprReportSnapshot(
    ReportRequest request,
    String projectName,
    String currencyCode,
    List<DailyProgressReportResponse> dprs,   // APPROVED, window+filter scoped, hydrated
    List<DprIssueRow> issues,
    DailyCostReportResponse cost,
    CapacityUtilizationReport capacity,
    SupervisorPerformanceReport supervisorPerf,
    List<String> voiceTranscripts,            // filled by Task 9

    // ── Consolidated report additions (Phase 2, 2026-08-05) — canonical sources only ──
    /** DBS project rows per day in the window — DAY-basis figures (income/expense/contribution
     *  are per-day columns; the period endpoint's BOQ cumulative columns are the known P0.3
     *  defect and are deliberately not used here). */
    List<DbsProjectDayResponse> dbsDays,
    /** Per-supervisor money summed across the window's DBS day rows. */
    List<DbsSupTotal> dbsSupervisors,
    /** BOQ lines mapped from the STORED split-corrected columns (same numbers as the BOQ tab). */
    List<BoqCostRow> boqRows,
    /** Canonical EVM snapshot from CostService.getCostSummary (same engine as Costs/EVM tabs). */
    CostSummaryDto evm,

    // ── DPR-agent-row additions (2026-08-10): supervisor performance + commodity summary ──
    /** Per supervisor (id-when-present-else-name identity): DPRs/qty for the reference day and
     *  the whole window, plus DBS money — all derived from the rows above, no new engines. */
    List<SupervisorPerfRow> supervisorPerformance,
    /** The latest report_date among the window's approved DPRs — the "day" bucket's anchor. */
    java.time.LocalDate referenceDay,
    /** BOQ-level executed quantities: reference-calendar-month vs stored till-date + % complete. */
    List<CommodityRow> commodityBoq,
    /** Activity-level executed quantities: reference month + window total (no contract basis). */
    List<CommodityRow> commodityActivities,

    // ── Material-agent-row additions (2026-08-11): store availability + issued-vs-reported ──
    /** Store availability from MaterialBalanceService; tracked=false → "stock not tracked". */
    com.bipros.resource.application.dto.MaterialAvailabilityResult materialAvailability,
    /** Nonzero issued-vs-reported variances (flag only — DBS costing awaits client Q20). */
    List<com.bipros.resource.application.dto.SupervisorMaterialRow> supervisorMaterialVariances
) {
    public record DbsSupTotal(String name, BigDecimal income, BigDecimal expense, BigDecimal contribution) {}

    public record SupervisorPerfRow(String name, long filedDay, BigDecimal qtyDay,
                                    long filedWindow, BigDecimal qtyWindow,
                                    BigDecimal income, BigDecimal expense, BigDecimal contribution) {}

    public record CommodityRow(String label, String unit, BigDecimal contractedQty,
                               BigDecimal qtyMonth, BigDecimal qtyToDate, BigDecimal pctComplete) {}

    public record BoqCostRow(String itemNo, String description, BigDecimal boqRate,
                             BigDecimal budgetedRate, BigDecimal actualRate,
                             BigDecimal qtyExecuted, BigDecimal percentComplete,
                             BigDecimal costVariance) {}
}
