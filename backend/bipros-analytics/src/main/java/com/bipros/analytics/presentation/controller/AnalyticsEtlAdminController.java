package com.bipros.analytics.presentation.controller;

import com.bipros.analytics.application.dto.BackfillResponse;
import com.bipros.analytics.application.service.BackfillService;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnalyticsEtlAdminController {

    private final BackfillService backfillService;

    /**
     * Truncates the named ClickHouse table, resets its watermark, then re-runs the matching
     * {@link com.bipros.analytics.etl.SyncHandler} from epoch. Synchronous — completes in
     * seconds for typical dev datasets. Returns row counts before/after for quick sanity-check.
     */
    @PostMapping("/backfill")
    public ApiResponse<BackfillResponse> backfill(@RequestParam("table") String table) {
        return ApiResponse.ok(backfillService.backfill(table));
    }
}
