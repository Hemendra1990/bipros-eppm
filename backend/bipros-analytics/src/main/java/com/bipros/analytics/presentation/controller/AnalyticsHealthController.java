package com.bipros.analytics.presentation.controller;

import com.bipros.analytics.application.dto.AnalyticsHealthResponse;
import com.bipros.analytics.application.service.AnalyticsHealthService;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only observability for the analytics assistant. Single endpoint
 * returns a consolidated payload (watermarks, hourly metrics, top users,
 * top errors) so the dashboard renders with one round trip.
 *
 * <p>Path is intentionally distinct from {@code AnalyticsEtlAdminController}
 * (which manages the ETL — backfill etc.) since this controller is purely
 * read-only observability.
 */
@RestController
@RequestMapping("/v1/admin/analytics")
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnalyticsHealthController {

    private final AnalyticsHealthService service;

    @GetMapping("/health")
    public ApiResponse<AnalyticsHealthResponse> health(
            @RequestParam(defaultValue = "24") int windowHours) {
        return ApiResponse.ok(service.health(windowHours));
    }
}
