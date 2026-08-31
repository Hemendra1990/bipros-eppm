package com.bipros.api.controller;

import com.bipros.api.notification.DprAlertConfig;
import com.bipros.common.dto.ApiResponse;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.application.dto.IdleStockRow;
import com.bipros.resource.application.service.MaterialIdleStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Outstanding-material check for one DPR, rendered as a panel on the DPR detail / approval view
 * so the approver sees the situation before approving.
 *
 * <p>Lives in {@code bipros-api} rather than on {@code DailyProgressReportController} because the
 * thresholds come from {@link DprAlertConfig}, which bipros-project cannot see (dependencies flow
 * inward). The route still sits under the DPR path so the frontend reads naturally.
 *
 * <p>Figures always count APPROVED DPR lines only. While this DPR is still SUBMITTED its own
 * quantities are therefore not yet included — {@code approvedOnly} says so, and the panel labels
 * it, rather than the endpoint duplicating the percent-complete engine to project them.
 */
@RestController
@RequestMapping("/v1/projects/{projectId}")
@RequiredArgsConstructor
public class MaterialIdleCheckController {

    private final DailyProgressReportRepository dprRepository;
    private final MaterialIdleStockService idleStockService;
    private final DprAlertConfig alertConfig;

    public record MaterialIdleCheckResponse(boolean approvedOnly, List<IdleStockRow> rows) {}

    /** Outstanding material tagged to one activity — the closeout flag on the activity drawer. */
    @GetMapping("/activities/{activityId}/material-idle")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'ACTIVITY.READ')")
    public ResponseEntity<ApiResponse<List<IdleStockRow>>> forActivity(
            @PathVariable UUID projectId, @PathVariable UUID activityId) {
        List<IdleStockRow> rows = idleStockService
            .evaluate(projectId, LocalDate.now(), alertConfig.idleThresholds()).stream()
            .filter(r -> r.alerting() && activityId.equals(r.activityId()))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping("/dpr/{dprId}/material-idle-check")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'DPR.READ')")
    public ResponseEntity<ApiResponse<MaterialIdleCheckResponse>> check(
            @PathVariable UUID projectId, @PathVariable UUID dprId) {
        DailyProgressReport dpr = dprRepository.findById(dprId).orElse(null);
        if (dpr == null || !projectId.equals(dpr.getProjectId()) || dpr.getSupervisorUserId() == null) {
            return ResponseEntity.ok(ApiResponse.ok(new MaterialIdleCheckResponse(false, List.of())));
        }
        List<IdleStockRow> rows = idleStockService.evaluateForCustodian(
                projectId, dpr.getSupervisorUserId(), LocalDate.now(), alertConfig.idleThresholds())
            .stream().filter(IdleStockRow::alerting).toList();
        boolean approvedOnly = dpr.getApprovalStatus() != DprApprovalStatus.APPROVED;
        return ResponseEntity.ok(ApiResponse.ok(new MaterialIdleCheckResponse(approvedOnly, rows)));
    }
}
