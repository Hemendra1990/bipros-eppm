package com.bipros.api.dprreport;

import com.bipros.common.dto.ApiResponse;
import com.bipros.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read API for stored Daily DPR Reports — backs the in-app report view the "DPR report ready"
 * bell notification links to (added with the 2026-08-05 port; the branch served this through the
 * retired command-center controller).
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/dpr-reports")
@RequiredArgsConstructor
public class DprReportController {

    private final DprAgentReportRepository repository;

    /** Latest 20 reports, newest first — list entries without the heavy HTML body. */
    public record ReportSummary(UUID id, String trigger, LocalDate windowFrom, LocalDate windowTo,
                                String windowLabel, Instant generatedAt, String status,
                                String deliveryStatus, String deliveredTo) {
    }

    @GetMapping
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'REPORT.READ')")
    public ResponseEntity<ApiResponse<List<ReportSummary>>> list(@PathVariable UUID projectId) {
        List<ReportSummary> out = repository.findTop20ByProjectIdOrderByGeneratedAtDesc(projectId).stream()
                .map(r -> new ReportSummary(r.getId(), r.getTrigger(), r.getWindowFrom(), r.getWindowTo(),
                        r.getWindowLabel(), r.getGeneratedAt(), r.getStatus(),
                        r.getDeliveryStatus(), r.getDeliveredTo()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** One report including the rendered HTML body and any error message. */
    public record ReportDetail(UUID id, String trigger, String windowLabel, Instant generatedAt,
                               String status, String deliveryStatus, String deliveredTo,
                               String summary, String htmlBody, String errorMessage) {
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'REPORT.READ')")
    public ResponseEntity<ApiResponse<ReportDetail>> get(@PathVariable UUID projectId,
                                                         @PathVariable UUID reportId) {
        DprAgentReport r = repository.findById(reportId)
                .filter(row -> projectId.equals(row.getProjectId()))
                .orElseThrow(() -> new ResourceNotFoundException("DPR report", reportId));
        return ResponseEntity.ok(ApiResponse.ok(new ReportDetail(r.getId(), r.getTrigger(),
                r.getWindowLabel(), r.getGeneratedAt(), r.getStatus(), r.getDeliveryStatus(),
                r.getDeliveredTo(), r.getSummary(), r.getHtmlBody(), r.getErrorMessage())));
    }
}
