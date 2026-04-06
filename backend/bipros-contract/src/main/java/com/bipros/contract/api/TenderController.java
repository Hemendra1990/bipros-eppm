package com.bipros.contract.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.common.dto.PagedResponse;
import com.bipros.contract.application.dto.TenderRequest;
import com.bipros.contract.application.dto.TenderResponse;
import com.bipros.contract.application.service.TenderService;
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
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/projects/{projectId}/tenders")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class TenderController {

    private final TenderService tenderService;

    @PostMapping
    public ResponseEntity<ApiResponse<TenderResponse>> create(
        @PathVariable UUID projectId,
        @Valid @RequestBody TenderRequest request) {
        TenderResponse response = tenderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TenderResponse>>> listByProject(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        PagedResponse<TenderResponse> response = tenderService.listByProject(projectId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenderResponse>> getById(
        @PathVariable UUID projectId,
        @PathVariable UUID id) {
        TenderResponse response = tenderService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TenderResponse>> update(
        @PathVariable UUID projectId,
        @PathVariable UUID id,
        @Valid @RequestBody TenderRequest request) {
        TenderResponse response = tenderService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID projectId,
        @PathVariable UUID id) {
        tenderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plan/{procurementPlanId}")
    public ResponseEntity<ApiResponse<List<TenderResponse>>> listByProcurementPlan(
        @PathVariable UUID projectId,
        @PathVariable UUID procurementPlanId) {
        List<TenderResponse> response = tenderService.listByProcurementPlan(procurementPlanId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
