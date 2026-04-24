package com.bipros.risk.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.risk.application.dto.CreateRiskRequest;
import com.bipros.risk.application.dto.CreateRiskResponseRequest;
import com.bipros.risk.application.dto.RiskResponseDto;
import com.bipros.risk.application.dto.RiskSummary;
import com.bipros.risk.application.service.RiskService;
import com.bipros.risk.domain.model.RiskStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/risks")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VIEWER')")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @PostMapping
    public ResponseEntity<ApiResponse<RiskSummary>> createRisk(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateRiskRequest request) {
        RiskSummary risk = riskService.createRisk(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(risk));
    }

    /**
     * Aggregate risk summary. Mapped BEFORE {@link #getRisk} so Spring picks up the literal
     * "summary" segment rather than trying to convert it to a UUID path variable.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRisksSummary(
        @PathVariable UUID projectId,
        @RequestParam(required = false) RiskStatus status) {
        List<RiskSummary> risks = riskService.listRisks(projectId, status);
        BigDecimal exposure = riskService.calculateRiskExposure(projectId);
        Map<String, List<RiskSummary>> matrix = riskService.getRiskMatrix(projectId);
        Map<String, Object> body = Map.of(
            "totalRisks", risks.size(),
            "exposure", exposure,
            "byStatus", risks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    r -> r.getStatus() != null ? r.getStatus().name() : "UNKNOWN",
                    java.util.stream.Collectors.counting())),
            "matrix", matrix
        );
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/{riskId}")
    public ResponseEntity<ApiResponse<RiskSummary>> getRisk(
        @PathVariable UUID projectId,
        @PathVariable UUID riskId) {
        RiskSummary risk = riskService.getRisk(projectId, riskId);
        return ResponseEntity.ok(ApiResponse.ok(risk));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RiskSummary>>> listRisks(
        @PathVariable UUID projectId,
        @RequestParam(required = false) RiskStatus status) {
        List<RiskSummary> risks = riskService.listRisks(projectId, status);
        return ResponseEntity.ok(ApiResponse.ok(risks));
    }

    @PutMapping("/{riskId}")
    public ResponseEntity<ApiResponse<RiskSummary>> updateRisk(
        @PathVariable UUID projectId,
        @PathVariable UUID riskId,
        @Valid @RequestBody CreateRiskRequest request) {
        RiskSummary risk = riskService.updateRisk(projectId, riskId, request);
        return ResponseEntity.ok(ApiResponse.ok(risk));
    }

    @DeleteMapping("/{riskId}")
    public ResponseEntity<ApiResponse<Void>> deleteRisk(
        @PathVariable UUID projectId,
        @PathVariable UUID riskId) {
        riskService.deleteRisk(projectId, riskId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/matrix")
    public ResponseEntity<ApiResponse<Map<String, List<RiskSummary>>>> getRiskMatrix(
        @PathVariable UUID projectId) {
        Map<String, List<RiskSummary>> matrix = riskService.getRiskMatrix(projectId);
        return ResponseEntity.ok(ApiResponse.ok(matrix));
    }

    @GetMapping("/exposure")
    public ResponseEntity<ApiResponse<BigDecimal>> getRiskExposure(
        @PathVariable UUID projectId) {
        BigDecimal exposure = riskService.calculateRiskExposure(projectId);
        return ResponseEntity.ok(ApiResponse.ok(exposure));
    }

    @PostMapping("/{riskId}/responses")
    public ResponseEntity<ApiResponse<RiskResponseDto>> addResponse(
        @PathVariable UUID projectId,
        @PathVariable UUID riskId,
        @Valid @RequestBody CreateRiskResponseRequest request) {
        RiskResponseDto response = riskService.addResponse(projectId, riskId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(response));
    }

    @GetMapping("/{riskId}/responses")
    public ResponseEntity<ApiResponse<List<RiskResponseDto>>> getResponses(
        @PathVariable UUID projectId,
        @PathVariable UUID riskId) {
        List<RiskResponseDto> responses = riskService.getResponses(projectId, riskId);
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PutMapping("/{riskId}/responses/{responseId}")
    public ResponseEntity<ApiResponse<RiskResponseDto>> updateResponse(
        @PathVariable UUID projectId,
        @PathVariable UUID riskId,
        @PathVariable UUID responseId,
        @Valid @RequestBody CreateRiskResponseRequest request) {
        RiskResponseDto response = riskService.updateResponse(projectId, riskId, responseId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
