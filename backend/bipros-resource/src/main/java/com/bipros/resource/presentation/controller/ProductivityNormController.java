package com.bipros.resource.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.application.dto.CreateProductivityNormRequest;
import com.bipros.resource.application.dto.ProductivityNormResponse;
import com.bipros.resource.application.dto.ResolvedNormResponse;
import com.bipros.resource.application.service.ProductivityNormService;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.service.ProductivityNormLookupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/productivity-norms")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class ProductivityNormController {

  private final ProductivityNormService service;
  private final ProductivityNormLookupService lookupService;

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
  public ResponseEntity<ApiResponse<ProductivityNormResponse>> create(
      @Valid @RequestBody CreateProductivityNormRequest request) {
    log.info("POST /v1/productivity-norms - type={}, workActivityId={}",
        request.normType(), request.workActivityId());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request)));
  }

  @PostMapping("/bulk")
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
  public ResponseEntity<ApiResponse<List<ProductivityNormResponse>>> createBulk(
      @Valid @RequestBody List<CreateProductivityNormRequest> requests) {
    log.info("POST /v1/productivity-norms/bulk - count={}", requests.size());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createBulk(requests)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<ProductivityNormResponse>>> list(
      @RequestParam(required = false) ProductivityNormType normType,
      @RequestParam(required = false) UUID workActivityId) {
    log.info("GET /v1/productivity-norms - normType={}, workActivityId={}", normType, workActivityId);
    if (workActivityId != null) {
      return ResponseEntity.ok(ApiResponse.ok(service.listByWorkActivity(workActivityId)));
    }
    return ResponseEntity.ok(ApiResponse.ok(service.list(normType)));
  }

  @GetMapping("/lookup")
  public ResponseEntity<ApiResponse<ResolvedNormResponse>> lookup(
      @RequestParam UUID workActivityId,
      @RequestParam(required = false) UUID resourceId) {
    log.info("GET /v1/productivity-norms/lookup - workActivityId={}, resourceId={}", workActivityId, resourceId);
    return ResponseEntity.ok(ApiResponse.ok(
        ResolvedNormResponse.from(lookupService.resolve(workActivityId, resourceId))));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ProductivityNormResponse>> get(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
  public ResponseEntity<ApiResponse<ProductivityNormResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateProductivityNormRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(service.update(id, request)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
