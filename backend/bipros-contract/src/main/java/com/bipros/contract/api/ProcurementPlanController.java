package com.bipros.contract.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.common.dto.PagedResponse;
import com.bipros.contract.application.dto.ProcurementPlanRequest;
import com.bipros.contract.application.dto.ProcurementPlanResponse;
import com.bipros.contract.application.service.ProcurementPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/procurement-plans")
@RequiredArgsConstructor
public class ProcurementPlanController {

    private final ProcurementPlanService procurementPlanService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProcurementPlanResponse>> create(
        @PathVariable UUID projectId,
        @Valid @RequestBody ProcurementPlanRequest request) {
        ProcurementPlanResponse response = procurementPlanService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProcurementPlanResponse>>> listByProject(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        PagedResponse<ProcurementPlanResponse> response = procurementPlanService.listByProject(projectId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcurementPlanResponse>> getById(
        @PathVariable UUID projectId,
        @PathVariable UUID id) {
        ProcurementPlanResponse response = procurementPlanService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcurementPlanResponse>> update(
        @PathVariable UUID projectId,
        @PathVariable UUID id,
        @Valid @RequestBody ProcurementPlanRequest request) {
        ProcurementPlanResponse response = procurementPlanService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID projectId,
        @PathVariable UUID id) {
        procurementPlanService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/wbs/{wbsNodeId}")
    public ResponseEntity<ApiResponse<List<ProcurementPlanResponse>>> listByWbsNode(
        @PathVariable UUID projectId,
        @PathVariable UUID wbsNodeId) {
        List<ProcurementPlanResponse> response = procurementPlanService.listByWbsNode(wbsNodeId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
