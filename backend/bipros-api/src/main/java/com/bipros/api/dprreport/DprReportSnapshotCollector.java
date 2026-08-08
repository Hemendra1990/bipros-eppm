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

        var issues = issueService.list(r.projectId(), null, null, null, oneSupervisor, null, r.from(), r.to(), null);
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

        return new DprReportSnapshot(r, project.getName(), project.getBudgetCurrency(),
            dprs, issues, cost, capacity, supervisorPerf, voiceTranscripts,
            dbsDays, dbsSupervisors, boqRows, evm);
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    private static boolean isEmpty(List<?> l) { return l == null || l.isEmpty(); }
}
