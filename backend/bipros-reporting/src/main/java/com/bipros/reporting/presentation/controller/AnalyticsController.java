package com.bipros.reporting.presentation.controller;

import com.bipros.analytics.application.dto.AnalyticsAssistantRequest;
import com.bipros.analytics.application.dto.AnalyticsAssistantResponse;
import com.bipros.analytics.application.service.AnalyticsAssistantService;
import com.bipros.common.dto.ApiResponse;
import com.bipros.reporting.application.dto.AnalyticsQueryDto;
import com.bipros.reporting.application.service.AnalyticsQueryService;
import com.bipros.security.application.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase-2: delegates the natural-language analytics path to {@link AnalyticsAssistantService}.
 * Per-tool authorization is enforced inside the orchestrator and individual tool
 * handlers (each call {@code ProjectAccessService.requireRead}), so the class-level
 * {@code @PreAuthorize} only requires authentication. The legacy
 * {@link AnalyticsQueryService} is retained to power conversation history under
 * {@code /queries}.
 */
@RestController
@RequestMapping("/v1/analytics")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AnalyticsController {

    private final Optional<AnalyticsAssistantService> analyticsAssistantService;
    private final AnalyticsQueryService analyticsQueryService;
    private final CurrentUserService currentUserService;

    /** Submit a natural-language analytics query. */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<AnalyticsAssistantResponse>> submitQuery(
            @Valid @RequestBody AnalyticsAssistantRequest request) {
        if (analyticsAssistantService.isEmpty()) {
            AnalyticsAssistantResponse disabled = new AnalyticsAssistantResponse(
                    "Analytics assistant is disabled on this deployment. "
                            + "Set bipros.analytics.assistant.enabled=true to enable.",
                    null, List.of(), List.of(), null, null, null, null, "DISABLED");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.ok(disabled));
        }
        AnalyticsAssistantResponse result = analyticsAssistantService.get().handle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** Conversation history for the current user. */
    @GetMapping("/queries")
    public ResponseEntity<ApiResponse<List<AnalyticsQueryDto>>> getQueryHistory(
            @RequestParam(defaultValue = "20") int limit) {
        UUID userId = currentUserService.getCurrentUserId();
        List<AnalyticsQueryDto> history = analyticsQueryService.getQueryHistory(
                userId == null ? null : userId.toString(), limit);
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
