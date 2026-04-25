package com.bipros.resource.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.application.dto.CreateResourceTypeDefRequest;
import com.bipros.resource.application.dto.ResourceTypeDefResponse;
import com.bipros.resource.application.dto.UpdateResourceTypeDefRequest;
import com.bipros.resource.application.service.ResourceTypeDefService;
import com.bipros.resource.domain.model.ResourceType;
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
@RequestMapping("/v1/resource-types")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VIEWER')")
@RequiredArgsConstructor
@Slf4j
public class ResourceTypeDefController {

  private final ResourceTypeDefService service;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ResourceTypeDefResponse>>> list(
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) ResourceType baseCategory) {
    log.info("GET /v1/resource-types - active={}, baseCategory={}", active, baseCategory);
    return ResponseEntity.ok(ApiResponse.ok(service.list(active, baseCategory)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ResourceTypeDefResponse>> get(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<ResourceTypeDefResponse>> create(
      @Valid @RequestBody CreateResourceTypeDefRequest request) {
    log.info("POST /v1/resource-types - code={}", request.code());
    ResourceTypeDefResponse created = service.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<ResourceTypeDefResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateResourceTypeDefRequest request) {
    log.info("PUT /v1/resource-types/{}", id);
    return ResponseEntity.ok(ApiResponse.ok(service.update(id, request)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
    log.info("DELETE /v1/resource-types/{}", id);
    service.delete(id);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
