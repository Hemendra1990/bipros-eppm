package com.bipros.analytics.presentation.controller;

import com.bipros.analytics.application.dto.LlmProviderRequest;
import com.bipros.analytics.application.dto.LlmProviderResponse;
import com.bipros.analytics.application.dto.TestConnectionResponse;
import com.bipros.analytics.application.dto.UsageSummaryResponse;
import com.bipros.analytics.application.service.AnalyticsUsageService;
import com.bipros.analytics.application.service.LlmProviderService;
import com.bipros.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/llm-providers")
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderService service;
    private final AnalyticsUsageService usageService;

    @GetMapping
    public ApiResponse<List<LlmProviderResponse>> list() {
        return ApiResponse.ok(service.listMine());
    }

    /**
     * Per-user usage rollup for the Usage tab. Returns daily aggregates by provider
     * for the calling user; defaults to last 30 days, capped at 90.
     */
    @GetMapping("/me/usage")
    public ApiResponse<UsageSummaryResponse> myUsage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(usageService.summaryForCurrentUser(from, to));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LlmProviderResponse>> create(@Valid @RequestBody LlmProviderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.create(req)));
    }

    @PostMapping("/{id}/default")
    public ApiResponse<LlmProviderResponse> setDefault(@PathVariable UUID id) {
        return ApiResponse.ok(service.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<TestConnectionResponse> test(@PathVariable UUID id) {
        return ApiResponse.ok(service.testConnection(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
