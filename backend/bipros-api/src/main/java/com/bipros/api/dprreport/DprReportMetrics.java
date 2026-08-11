package com.bipros.api.dprreport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DprReportMetrics {
    public record Anomaly(String code, String label, String severity, String detail, double value) {}
    public record RoleEfficiency(String role, Double utilizationPct, double costImplication, String severity) {}
    public record CostVarianceItem(String label, double variance, double variancePct) {}

    // ── Consolidated report blocks (Phase 2, 2026-08-05) ──
    public record DayMoney(String date, double income, double expense, double contribution, int dprCount) {}
    public record SupMoney(String name, double income, double expense, double contribution) {}
    public record BoqVar(String itemNo, String label, double boqRate, double budgetedRate,
                         double actualRate, double qtyExecuted, double percentComplete, double variance) {}
    public record MaterialUse(String name, String unit, double qty, double cost) {}
    /** EVM-agent-row 2026-08-11: sv/cv/etc/tcpi added so the mail mirrors the EVM tab's full
     *  card set. cpi/spi/tcpi are nullable exactly like the engine (cpi null when AC = 0, spi null
     *  when PV = 0, tcpi null when BAC = AC) — a genuine 0.0 is a real score (nothing earned),
     *  NOT "no data", and the two must never collapse into one value. */
    public record EvmBlock(double bac, double pv, double ev, double ac, Double cpi, Double spi,
                           double eac, double vac, double pctComplete,
                           double sv, double cv, double etc, Double tcpi) {}
    public record SupWork(String name, int dprCount, int activityCount, double qty) {}
    public record CatCount(String name, int count) {}
    /** Supervisor performance (day + window) — DPR-agent-row addition 2026-08-10. */
    public record SupPerf(String name, long filedDay, double qtyDay,
                          long filedWindow, double qtyWindow, Double contribution) {}
    /** Commodity summary line (BOQ- or activity-level executed quantities). */
    public record CommodityLine(String label, String unit, Double contractedQty,
                                double qtyMonth, double qtyToDate, Double pctComplete) {}
    /** Resource-wise capacity line (Capacity Util. tab pass-through) — day + window buckets.
     *  Capacity-agent-row addition 2026-08-10. */
    public record CapacityLine(String role, Double dayBudget, Double dayCounted, Double dayEff,
                               Double qty, Double budgetDays, Double countedDays, Double eff,
                               Double cost) {}
    /** Store availability line (MaterialBalanceService pass-through) — Material-agent-row 2026-08-11. */
    public record MaterialAvail(String name, String unit, Double receivedWindow, Double issuedWindow,
                                Double consumedWindow, Double storeClosing, Double daysOfCover,
                                String alert) {}
    /** Supervisor issued-vs-reported variance line (flag only — DBS costing awaits Q20). */
    public record SupMaterialVar(String supervisor, String material, String unit,
                                 double issuedToDate, double reportedToDate, double varianceQty,
                                 Double varianceValue) {}
    /** Activity costing line (Costing-agent-row 2026-08-11): window actual vs value at budgeted
     *  rates vs value at BOQ rates. Variances = actual − value (positive = overspend). */
    public record ActivityCost(String activity, Double qty, String unit, double actualCost,
                               Double budgetedValue, Double boqValue,
                               Double varVsBudgeted, Double varVsBoq) {}

    public String projectName;
    public String currencyCode;
    public int totalDprs;
    public double totalQtyExecuted;
    public double totalActualCost;
    public double totalBudgetedCost;
    public double totalCostVariance;
    public int openIssues;
    public int criticalIssues;
    public int safetyIncidents;
    public List<RoleEfficiency> roleEfficiencies = new ArrayList<>();
    public List<CostVarianceItem> costVariances = new ArrayList<>();
    public List<Anomaly> anomalies = new ArrayList<>();
    public Set<String> allowedNumbers = new LinkedHashSet<>();

    // Consolidated sections — all from canonical engines (DBS day rows, stored BOQ columns,
    // CostService EVM). Null/empty when the source had nothing for the window.
    public List<DayMoney> dbsDays = new ArrayList<>();
    public List<SupMoney> dbsSupervisors = new ArrayList<>();
    public double dbsIncome;
    public double dbsExpense;
    public double dbsContribution;
    public List<BoqVar> boqTopVariances = new ArrayList<>();
    public double boqTotalVariance;
    public List<MaterialUse> materials = new ArrayList<>();
    public EvmBlock evm;   // null when no budget configured
    public List<SupWork> supervisorWork = new ArrayList<>();
    public List<CatCount> issueCategories = new ArrayList<>();
    public List<SupPerf> supervisorPerformance = new ArrayList<>();
    public List<CapacityLine> capacityManpower = new ArrayList<>();
    public List<CapacityLine> capacityEquipment = new ArrayList<>();
    public CapacityLine capacityManpowerTotal;   // null when the section had no rows
    public CapacityLine capacityEquipmentTotal;
    public List<CommodityLine> commodityBoq = new ArrayList<>();
    public List<CommodityLine> commodityActivities = new ArrayList<>();
    /** Store availability — empty when the project has no store data (materialTracked=false). */
    public boolean materialTracked;
    public List<MaterialAvail> materialAvailability = new ArrayList<>();
    /** Nonzero issued-vs-reported variances (issued > reported first); empty = no anomalies. */
    public List<SupMaterialVar> supervisorMaterialVariances = new ArrayList<>();
    /** Top activities by |actual − budgeted value| for the window; totals row over ALL activities. */
    public List<ActivityCost> activityCosting = new ArrayList<>();
    public ActivityCost activityCostingTotal;   // null when the window had no cost rows
    public int activityCostingCount;            // total activities before the top-N cut
    /** ISO date of the "day" bucket (latest report_date among the window's approved DPRs). */
    public String referenceDay;
    /** Absolute link to the project's EVM tab (EVM-agent-row 2026-08-11: "dashboard should be
     *  available") — set by DprReportService from DprAlertConfig.appBaseUrl; null in unit tests. */
    public String evmDashboardUrl;
}
