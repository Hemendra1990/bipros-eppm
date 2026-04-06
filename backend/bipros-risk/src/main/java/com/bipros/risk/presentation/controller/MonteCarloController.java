package com.bipros.risk.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.risk.application.dto.MonteCarloSimulationDto;
import com.bipros.risk.application.service.MonteCarloService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/projects/{projectId}/monte-carlo")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class MonteCarloController {

    private final MonteCarloService monteCarloService;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<MonteCarloSimulationDto>> runSimulation(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "10000") int iterations) {
        MonteCarloSimulationDto result = monteCarloService.runSimulation(projectId, iterations);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(result));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<MonteCarloSimulationDto>> getLatestSimulation(
        @PathVariable UUID projectId) {
        MonteCarloSimulationDto result = monteCarloService.getLatestSimulation(projectId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{simulationId}")
    public ResponseEntity<ApiResponse<MonteCarloSimulationDto>> getSimulation(
        @PathVariable UUID projectId,
        @PathVariable UUID simulationId) {
        MonteCarloSimulationDto result = monteCarloService.getSimulation(simulationId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MonteCarloSimulationDto>>> listSimulations(
        @PathVariable UUID projectId) {
        List<MonteCarloSimulationDto> results = monteCarloService.listProjectSimulations(projectId);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
