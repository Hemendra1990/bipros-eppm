package com.bipros.project.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.DprAnalyticsResponse;
import com.bipros.project.application.service.DprAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DPR-performance analytics for the DPR tab (AI Agent sheet, DPR row: "Analysis of DPR
 * performance to be shown on DPR dash board"). Read-only aggregation over existing rows —
 * nothing is stored.
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/dpr")
@RequiredArgsConstructor
public class DprAnalyticsController {

    private final DprAnalyticsService analyticsService;

    @GetMapping("/analytics")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'DPR.READ')")
    public ResponseEntity<ApiResponse<DprAnalyticsResponse>> analytics(
            @PathVariable UUID projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.analytics(projectId, from, to)));
    }
}
