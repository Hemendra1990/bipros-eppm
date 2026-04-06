package com.bipros.resource.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.application.dto.CreateResourceRequest;
import com.bipros.resource.application.dto.ResourceResponse;
import com.bipros.resource.application.service.ResourceService;
import com.bipros.resource.domain.model.ResourceStatus;
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
@RequestMapping("/v1/resources")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VIEWER')")
@RequiredArgsConstructor
@Slf4j
public class ResourceController {

  private final ResourceService resourceService;

  @PostMapping
  public ResponseEntity<ApiResponse<ResourceResponse>> createResource(
      @Valid @RequestBody CreateResourceRequest request) {
    log.info("POST /v1/resources - Creating resource: {}", request.code());
    ResourceResponse response = resourceService.createResource(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ResourceResponse>> getResource(@PathVariable UUID id) {
    log.info("GET /v1/resources/{} - Fetching resource", id);
    ResourceResponse response = resourceService.getResource(id);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<ResourceResponse>>> listResources(
      @RequestParam(required = false) ResourceType type,
      @RequestParam(required = false) ResourceStatus status) {
    log.info("GET /v1/resources - Listing resources, type={}, status={}", type, status);
    List<ResourceResponse> response;
    if (type != null) {
      response = resourceService.listResourcesByType(type);
    } else if (status != null) {
      response = resourceService.listResourcesByStatus(status);
    } else {
      response = resourceService.listResources();
    }
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/hierarchy/roots")
  public ResponseEntity<ApiResponse<List<ResourceResponse>>> listHierarchyRoots() {
    log.info("GET /v1/resources/hierarchy/roots - Listing hierarchy roots");
    List<ResourceResponse> response = resourceService.listResourceHierarchyRoots();
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{id}/children")
  public ResponseEntity<ApiResponse<List<ResourceResponse>>> listChildResources(@PathVariable UUID id) {
    log.info("GET /v1/resources/{}/children - Listing child resources", id);
    List<ResourceResponse> response = resourceService.listResourcesByParent(id);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<ResourceResponse>> updateResource(
      @PathVariable UUID id,
      @Valid @RequestBody CreateResourceRequest request) {
    log.info("PUT /v1/resources/{} - Updating resource", id);
    ResourceResponse response = resourceService.updateResource(id, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable UUID id) {
    log.info("DELETE /v1/resources/{} - Deleting resource", id);
    resourceService.deleteResource(id);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
