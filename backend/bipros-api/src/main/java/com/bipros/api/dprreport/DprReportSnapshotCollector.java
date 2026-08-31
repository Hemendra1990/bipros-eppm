package com.bipros.api.dprreport;

import com.bipros.project.application.dto.DailyProgressReportResponse;
import com.bipros.project.application.service.DailyProgressReportService;
import com.bipros.project.application.service.DprIssueService;
import com.bipros.project.application.service.DailyCostReportService;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.reporting.application.service.CapacityUtilizationReportService;
import com.bipros.reporting.application.service.SupervisorPerformanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DprReportSnapshotCollector {
    private final DailyProgressReportService dprService;
    private final DprIssueService issueService;
    private final DailyCostReportService costService;
    private final CapacityUtilizationReportService capacityService;
    private final SupervisorPerformanceReportService supervisorService;
    private final ProjectRepository projectRepository;
    private final DprVoiceNoteTranscriber voiceNoteTranscriber;
    // Consolidated report additions (Phase 2) — canonical engines only:
    private final com.bipros.dbs.service.DbsQueryService dbsQueryService;
    private final com.bipros.project.domain.repository.BoqItemRepository boqItemRepository;
    private final com.bipros.cost.application.service.CostService evmCostService;
    // Material-agent-row additions (2026-08-11):
    private final com.bipros.resource.application.service.MaterialBalanceService materialBalanceService;
    private final com.bipros.resource.application.service.SupervisorMaterialComparisonService supervisorMaterialComparisonService;

    @Transactional(readOnly = true)
    public DprReportSnapshot collect(ReportRequest r) {
        var project = projectRepository.findById(r.projectId()).orElseThrow();
        UUID oneSupervisor = (r.supervisorUserIds() != null && r.supervisorUserIds().size() == 1)
            ? r.supervisorUserIds().get(0) : null;

        var dprs = dprService.list(r.projectId(), r.from(), r.to(), null).stream()
            .filter(d -> d.approvalStatus() == DprApprovalStatus.APPROVED)
            .filter(d -> isEmpty(r.supervisorUserIds()) || r.supervisorUserIds().contains(d.supervisorUserId()))
            .filter(d -> isEmpty(r.activityIds()) || r.activityIds().contains(d.activityId()))
            .filter(d -> isEmpty(r.boqItemIds()) || r.boqItemIds().contains(d.boqItemId()))
            .toList();

        var issues = issueService.list(r.projectId(), null, null, null, oneSupervisor, null, r.from(), r.to(), null, null);
        var cost = costService.generate(r.projectId(), r.from(), r.to());
        var capacity = capacityService.build(r.projectId(), r.from(), r.to(), "RESOURCE_TYPE", null);
        var supervisorPerf = supervisorService.build(r.projectId(), oneSupervisor, r.from(), r.to(), 26);

        List<UUID> dprIds = dprs.stream().map(DailyProgressReportResponse::id).toList();
        List<String> voiceTranscripts = voiceNoteTranscriber.transcribeForDprs(dprIds);

        // ── DBS: day-basis rows only (per-day income/expense/contribution are correct; the
        // period endpoint's summed BOQ cumulative columns are the known P0.3 defect — avoided).
        List<com.bipros.dbs.api.dto.DbsProjectDayResponse> dbsDays = new java.util.ArrayList<>();
        java.util.Map<String, java.math.BigDecimal[]> supTotals = new java.util.LinkedHashMap<>();
        for (java.time.LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) {
            try {
                dbsDays.add(dbsQueryService.getProjectDay(r.projectId(), d));
                for (var s : dbsQueryService.listSupervisorsForScope(r.projectId(), d, null)) {
                    String name = s.supervisorName() == null ? "(unnamed)" : s.supervisorName();
                    java.math.BigDecimal[] t = supTotals.computeIfAbsent(name,
                        k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
                    t[0] = t[0].add(nz(s.totalIncome()));
                    t[1] = t[1].add(nz(s.totalExpense()));
                    t[2] = t[2].add(nz(s.contribution()));
                }
            } catch (Exception dayMissing) {
                // No DBS row for that day — a zero-activity day, not an error.
            }
        }
        List<DprReportSnapshot.DbsSupTotal> dbsSupervisors = supTotals.entrySet().stream()
            .map(e -> new DprReportSnapshot.DbsSupTotal(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
            .sorted((a, b) -> b.expense().compareTo(a.expense()))
            .toList();

        // ── Costing: the STORED split-corrected BOQ columns — identical to the BOQ tab.
        List<DprReportSnapshot.BoqCostRow> boqRows = boqItemRepository
            .findByProjectIdOrderByItemNoAsc(r.projectId()).stream()
            .map(b -> new DprReportSnapshot.BoqCostRow(b.getItemNo(), b.getDescription(), b.getBoqRate(),
                b.getBudgetedRate(), b.getActualRate(), b.getQtyExecutedToDate(),
                b.getPercentComplete(), b.getCostVariance()))
            .toList();

        // ── EVM: the canonical engine behind the Costs/EVM tabs.
        com.bipros.cost.application.dto.CostSummaryDto evm = null;
        try {
            evm = evmCostService.getCostSummary(r.projectId());
        } catch (Exception evmMissing) {
            // Report survives without the EVM strip (e.g. no budget configured yet).
        }

        // ── DPR-agent-row additions (2026-08-10): supervisor performance + commodity summary.
        // Derived entirely from the window's approved DPRs + the stored BOQ columns + the DBS
        // totals already collected above — no new calculation engines.
        java.time.LocalDate referenceDay = dprs.stream()
            .map(DailyProgressReportResponse::reportDate)
            .filter(java.util.Objects::nonNull)
            .max(java.time.LocalDate::compareTo).orElse(null);
        java.time.YearMonth referenceMonth = java.time.YearMonth.from(
            referenceDay != null ? referenceDay : r.to());

        java.util.Map<String, java.math.BigDecimal[]> perSup = new java.util.LinkedHashMap<>();
        java.util.Map<String, long[]> perSupCounts = new java.util.LinkedHashMap<>();
        for (var d : dprs) {
            String name = d.supervisorName() != null && !d.supervisorName().isBlank()
                ? d.supervisorName().trim()
                : (d.supervisorUserId() != null ? d.supervisorUserId().toString() : "(unnamed)");
            java.math.BigDecimal[] qty = perSup.computeIfAbsent(name,
                k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
            long[] counts = perSupCounts.computeIfAbsent(name, k -> new long[2]);
            qty[1] = qty[1].add(nz(d.qtyExecuted()));
            counts[1]++;
            if (referenceDay != null && referenceDay.equals(d.reportDate())) {
                qty[0] = qty[0].add(nz(d.qtyExecuted()));
                counts[0]++;
            }
        }
        java.util.Map<String, DprReportSnapshot.DbsSupTotal> moneyByName = new java.util.HashMap<>();
        for (var s : dbsSupervisors) moneyByName.putIfAbsent(s.name(), s);
        List<DprReportSnapshot.SupervisorPerfRow> supervisorPerformance = perSup.entrySet().stream()
            .map(e -> {
                var money = moneyByName.get(e.getKey());
                long[] counts = perSupCounts.get(e.getKey());
                return new DprReportSnapshot.SupervisorPerfRow(e.getKey(),
                    counts[0], e.getValue()[0], counts[1], e.getValue()[1],
                    money != null ? money.income() : null,
                    money != null ? money.expense() : null,
                    money != null ? money.contribution() : null);
            })
            .sorted((a, b) -> Long.compare(b.filedWindow(), a.filedWindow()))
            .toList();

        java.util.Map<String, java.math.BigDecimal> monthQtyByItem = new java.util.HashMap<>();
        java.util.Map<String, java.math.BigDecimal[]> actQty = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> actUnit = new java.util.HashMap<>();
        for (var d : dprs) {
            boolean inMonth = d.reportDate() != null
                && java.time.YearMonth.from(d.reportDate()).equals(referenceMonth);
            if (d.boqItemNo() != null && inMonth) {
                monthQtyByItem.merge(d.boqItemNo(), nz(d.qtyExecuted()), java.math.BigDecimal::add);
            }
            String act = d.activityName() != null && !d.activityName().isBlank() ? d.activityName() : "(unnamed)";
            java.math.BigDecimal[] q = actQty.computeIfAbsent(act,
                k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
            if (inMonth) q[0] = q[0].add(nz(d.qtyExecuted()));
            q[1] = q[1].add(nz(d.qtyExecuted()));
            if (d.unit() != null) actUnit.putIfAbsent(act, d.unit());
        }
        List<DprReportSnapshot.CommodityRow> commodityBoq = boqItemRepository
            .findByProjectIdOrderByItemNoAsc(r.projectId()).stream()
            .filter(b -> nz(b.getQtyExecutedToDate()).signum() > 0
                || monthQtyByItem.containsKey(b.getItemNo()))
            .map(b -> new DprReportSnapshot.CommodityRow(
                b.getItemNo() + " — " + trim60(b.getDescription()), b.getUnit(), b.getBoqQty(),
                monthQtyByItem.getOrDefault(b.getItemNo(), java.math.BigDecimal.ZERO),
                b.getQtyExecutedToDate(), b.getPercentComplete()))
            .toList();
        List<DprReportSnapshot.CommodityRow> commodityActivities = actQty.entrySet().stream()
            .map(e -> new DprReportSnapshot.CommodityRow(e.getKey(), actUnit.get(e.getKey()), null,
                e.getValue()[0], e.getValue()[1], null))
            .sorted((a, b) -> b.qtyToDate().compareTo(a.qtyToDate()))
            .toList();

        // ── Material availability + supervisor issued-vs-reported (Material-agent-row 2026-08-11).
        // Fail-safe: a store-side error must never sink the whole report.
        com.bipros.resource.application.dto.MaterialAvailabilityResult materialAvailability;
        try {
            materialAvailability = materialBalanceService.availability(r.projectId(), r.from(), r.to(), 3);
        } catch (Exception e) {
            materialAvailability = new com.bipros.resource.application.dto.MaterialAvailabilityResult(false, List.of());
        }
        List<com.bipros.resource.application.dto.SupervisorMaterialRow> supMaterialVariances;
        try {
            supMaterialVariances = supervisorMaterialComparisonService.compare(r.projectId(), r.to(), r.from())
                .stream().filter(v -> v.varianceQty().signum() != 0).toList();
        } catch (Exception e) {
            supMaterialVariances = List.of();
        }

        return new DprReportSnapshot(r, project.getName(), project.getBudgetCurrency(),
            dprs, issues, cost, capacity, supervisorPerf, voiceTranscripts,
            dbsDays, dbsSupervisors, boqRows, evm,
            supervisorPerformance, referenceDay, commodityBoq, commodityActivities,
            materialAvailability, supMaterialVariances);
    }

    private static String trim60(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= 60 ? t : t.substring(0, 57) + "…";
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    private static boolean isEmpty(List<?> l) { return l == null || l.isEmpty(); }
}
