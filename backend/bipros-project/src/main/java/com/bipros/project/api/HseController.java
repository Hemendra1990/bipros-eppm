package com.bipros.project.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.HseStatisticsResponse;
import com.bipros.project.application.dto.ProjectHseMetricsResponse;
import com.bipros.project.application.dto.UpdateProjectHseMetricsRequest;
import com.bipros.project.application.service.HseStatisticsService;
import com.bipros.project.application.service.ProjectHseMetricsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HSE (Health, Safety &amp; Environment) tab endpoints. {@code /metrics} is the manual-input
 * read/upsert (KM driven); the display-only {@code /statistics} aggregation is added alongside.
 * Read is gated {@code DPR.READ}; the metrics upsert is gated {@code DPR.UPDATE}
 * (provisional — revisited in the permissions pass).
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/hse")
@RequiredArgsConstructor
@Slf4j
public class HseController {

    private final ProjectHseMetricsService metricsService;
    private final HseStatisticsService statisticsService;

    @GetMapping("/metrics")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'DPR.READ')")
    public ResponseEntity<ApiResponse<ProjectHseMetricsResponse>> getMetrics(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getOrDefault(projectId)));
    }

    @GetMapping("/statistics")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'DPR.READ')")
    public ResponseEntity<ApiResponse<HseStatisticsResponse>> statistics(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.compute(projectId)));
    }

    @PutMapping("/metrics")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'DPR.UPDATE')")
    public ResponseEntity<ApiResponse<ProjectHseMetricsResponse>> putMetrics(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectHseMetricsRequest request) {
        log.info("PUT /v1/projects/{}/hse/metrics - km={} indirectManHours={}",
            projectId, request.kmDistanceDriven(), request.indirectManHours());
        return ResponseEntity.ok(ApiResponse.ok(metricsService.upsert(projectId, request)));
    }
}
