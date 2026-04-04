package com.bipros.evm.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.evm.application.dto.CalculateEvmRequest;
import com.bipros.evm.application.dto.EvmCalculationResponse;
import com.bipros.evm.application.dto.EvmSummaryResponse;
import com.bipros.evm.application.service.EvmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/evm")
@RequiredArgsConstructor
public class EvmController {

    private final EvmService evmService;

    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<EvmCalculationResponse>> calculateEvm(
            @PathVariable UUID projectId,
            @Valid @RequestBody CalculateEvmRequest request) {
        EvmCalculationResponse response = evmService.calculateEvm(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EvmCalculationResponse>> getLatestEvm(
            @PathVariable UUID projectId) {
        EvmCalculationResponse response = evmService.getLatestEvm(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<EvmCalculationResponse>>> getEvmHistory(
            @PathVariable UUID projectId) {
        List<EvmCalculationResponse> response = evmService.getEvmHistory(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/wbs/{wbsNodeId}")
    public ResponseEntity<ApiResponse<EvmCalculationResponse>> getEvmByWbs(
            @PathVariable UUID projectId,
            @PathVariable UUID wbsNodeId) {
        EvmCalculationResponse response = evmService.getEvmByWbs(projectId, wbsNodeId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<EvmSummaryResponse>> getSummary(
            @PathVariable UUID projectId) {
        EvmSummaryResponse response = evmService.getSummary(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
