package com.bipros.resource.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.application.dto.CreateResourceAssignmentRequest;
import com.bipros.resource.application.dto.ResourceAssignmentResponse;
import com.bipros.resource.application.dto.ResourceLevelingRequest;
import com.bipros.resource.application.dto.ResourceLevelingResponse;
import com.bipros.resource.application.dto.ResourceUsageEntry;
import com.bipros.resource.application.dto.UtilizationProfileEntry;
import com.bipros.resource.application.service.ResourceAssignmentService;
import com.bipros.resource.application.service.ResourceLevelingService;
import com.bipros.resource.domain.algorithm.LevelingMode;
import com.bipros.resource.domain.algorithm.LevelingResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/resource-assignments")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VIEWER')")
@RequiredArgsConstructor
@Slf4j
public class ResourceAssignmentController {

  private final ResourceAssignmentService assignmentService;
  private final ResourceLevelingService levelingService;

  @PostMapping
  public ResponseEntity<ApiResponse<ResourceAssignmentResponse>> assignResource(
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateResourceAssignmentRequest request) {
    log.info("POST /v1/projects/{}/resource-assignments - Assigning resource: {}",
        projectId, request.resourceId());
    ResourceAssignmentResponse response = assignmentService.assignResource(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ResourceAssignmentResponse>> getAssignment(
      @PathVariable UUID projectId,
      @PathVariable UUID id) {
    log.info("GET /v1/projects/{}/resource-assignments/{} - Fetching assignment", projectId, id);
    ResourceAssignmentResponse response = assignmentService.getAssignment(id);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<ResourceAssignmentResponse>>> listAssignments(
      @PathVariable UUID projectId) {
    log.info("GET /v1/projects/{}/resource-assignments - Listing assignments", projectId);
    List<ResourceAssignmentResponse> response = assignmentService.getAssignmentsByProject(projectId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/activity/{activityId}")
  public ResponseEntity<ApiResponse<List<ResourceAssignmentResponse>>> listAssignmentsByActivity(
      @PathVariable UUID projectId,
      @PathVariable UUID activityId) {
    log.info("GET /v1/projects/{}/resource-assignments/activity/{} - Listing assignments by activity",
        projectId, activityId);
    List<ResourceAssignmentResponse> response = assignmentService.getAssignmentsByActivity(activityId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/resource/{resourceId}")
  public ResponseEntity<ApiResponse<List<ResourceAssignmentResponse>>> listAssignmentsByResource(
      @PathVariable UUID projectId,
      @PathVariable UUID resourceId) {
    log.info("GET /v1/projects/{}/resource-assignments/resource/{} - Listing assignments by resource",
        projectId, resourceId);
    List<ResourceAssignmentResponse> response = assignmentService.getAssignmentsByResource(resourceId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/resource/{resourceId}/usage-profile")
  public ResponseEntity<ApiResponse<List<ResourceUsageEntry>>> getResourceUsageProfile(
      @PathVariable UUID projectId,
      @PathVariable UUID resourceId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    log.info("GET /v1/projects/{}/resource-assignments/resource/{}/usage-profile - "
            + "Fetching usage profile, startDate={}, endDate={}",
        projectId, resourceId, startDate, endDate);
    List<ResourceUsageEntry> response = assignmentService.getResourceUsageProfile(
        resourceId, startDate, endDate);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<ResourceAssignmentResponse>> updateAssignment(
      @PathVariable UUID projectId,
      @PathVariable UUID id,
      @Valid @RequestBody CreateResourceAssignmentRequest request) {
    log.info("PUT /v1/projects/{}/resource-assignments/{} - Updating assignment", projectId, id);
    ResourceAssignmentResponse response = assignmentService.updateAssignment(id, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> removeAssignment(
      @PathVariable UUID projectId,
      @PathVariable UUID id) {
    log.info("DELETE /v1/projects/{}/resource-assignments/{} - Removing assignment", projectId, id);
    assignmentService.removeAssignment(id);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  @PostMapping("/level-resources")
  public ResponseEntity<ApiResponse<LevelingResult>> levelResources(
      @PathVariable UUID projectId) {
    log.info("POST /v1/projects/{}/resource-assignments/level-resources - Starting resource leveling",
        projectId);
    LevelingResult result = levelingService.levelResources(projectId);
    return ResponseEntity.ok(ApiResponse.ok(result));
  }

  @PostMapping("/level")
  @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'SCHEDULER')")
  public ResponseEntity<ApiResponse<ResourceLevelingResponse>> levelResourcesWithMode(
      @PathVariable UUID projectId,
      @Valid @RequestBody ResourceLevelingRequest request) {
    log.info("POST /v1/projects/{}/resource-assignments/level - mode={}", projectId, request.mode());
    ResourceLevelingResponse response = levelingService.levelResourcesWithMode(
        projectId, request.mode(), request.resourceIds());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/utilization-profile")
  @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'SCHEDULER')")
  public ResponseEntity<ApiResponse<List<UtilizationProfileEntry>>> getUtilizationProfile(
      @PathVariable UUID projectId) {
    log.info("GET /v1/projects/{}/resource-assignments/utilization-profile", projectId);
    List<UtilizationProfileEntry> response = levelingService.getUtilizationProfile(projectId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
