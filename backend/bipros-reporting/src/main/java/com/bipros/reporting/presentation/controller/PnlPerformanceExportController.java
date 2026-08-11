package com.bipros.reporting.presentation.controller;

import com.bipros.cost.application.service.BoqMarginService;
import com.bipros.cost.application.service.BudgetedMarginService;
import com.bipros.cost.application.service.PerformanceRollupService;
import com.bipros.common.exception.ValidationException;
import com.bipros.reporting.infrastructure.export.PnlPerformanceExcelWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Report downloads for the Performance (P&L) screens — Access-Output sheet row 5: Engineer /
 * Project Control / QS / Site Manager / PM may download, Supervisor may not. Enforced as
 * COST.READ (which excludes Supervisor, same as the screens' view gate) AND REPORT.EXPORT.
 * Project-scoped guard, so project membership is enforced too.
 */
@RestController
@RequestMapping("/v1/projects/{projectId}")
@RequiredArgsConstructor
public class PnlPerformanceExportController {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final PerformanceRollupService performanceRollupService;
    private final BudgetedMarginService budgetedMarginService;
    private final BoqMarginService boqMarginService;
    private final PnlPerformanceExcelWriter writer;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/performance/export.xlsx")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'COST.READ') "
            + "and @projectAccess.hasProjectPermission(#projectId, 'REPORT.EXPORT')")
    public ResponseEntity<byte[]> downloadPerformance(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "M") String periodType) {
        byte[] bytes = writer.generatePerformance(
                performanceRollupService.rollup(projectId, periodType),
                lookupProjectName(projectId), periodLabel(periodType));
        return xlsx(bytes, "performance-" + periodType + "-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/pnl/{scope}/export.xlsx")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'COST.READ') "
            + "and @projectAccess.hasProjectPermission(#projectId, 'REPORT.EXPORT')")
    public ResponseEntity<byte[]> downloadPnl(
            @PathVariable UUID projectId,
            @PathVariable String scope,
            @RequestParam(defaultValue = "M") String periodType) {
        String projectName = lookupProjectName(projectId);
        byte[] bytes;
        if ("budgeted".equalsIgnoreCase(scope)) {
            bytes = writer.generatePnl("P&L vs Budgeted Unit Rates",
                    budgetedMarginService.summary(projectId),
                    budgetedMarginService.marginByPeriod(projectId, periodType),
                    budgetedMarginService.marginByBoqItem(projectId),
                    budgetedMarginService.marginByActivity(projectId),
                    projectName, periodLabel(periodType));
        } else if ("boq".equalsIgnoreCase(scope)) {
            bytes = writer.generatePnl("P&L vs BOQ Rates",
                    boqMarginService.summary(projectId),
                    boqMarginService.marginByPeriod(projectId, periodType),
                    boqMarginService.marginByBoqItem(projectId),
                    boqMarginService.marginByActivity(projectId),
                    projectName, periodLabel(periodType));
        } else {
            throw new ValidationException("Unknown P&L scope '" + scope + "' — use budgeted or boq");
        }
        return xlsx(bytes, "pnl-" + scope.toLowerCase() + "-" + LocalDate.now() + ".xlsx");
    }

    private static String periodLabel(String periodType) {
        return switch (periodType == null ? "M" : periodType.toUpperCase()) {
            case "D" -> "Daily";
            case "W" -> "Weekly";
            default -> "Monthly";
        };
    }

    private static ResponseEntity<byte[]> xlsx(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(bytes);
    }

    private String lookupProjectName(UUID projectId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                        "SELECT name FROM project.projects WHERE id = :id")
                .setParameter("id", projectId)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).toString();
    }
}
