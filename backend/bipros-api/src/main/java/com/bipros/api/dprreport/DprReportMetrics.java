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
    public record EvmBlock(double bac, double pv, double ev, double ac, double cpi, double spi,
                           double eac, double vac, double pctComplete) {}
    public record SupWork(String name, int dprCount, int activityCount, double qty) {}
    public record CatCount(String name, int count) {}

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
}
