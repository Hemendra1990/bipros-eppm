package com.bipros.activity.api;

import com.bipros.activity.application.dto.ActivityResponse;
import com.bipros.activity.application.dto.CreateActivityRequest;
import com.bipros.activity.application.dto.UpdateActivityRequest;
import com.bipros.activity.application.service.ActivityService;
import com.bipros.activity.application.service.ApplyActualsRequest;
import com.bipros.activity.application.service.GlobalChangeRequest;
import com.bipros.activity.application.service.GlobalChangeService;
import com.bipros.common.dto.ApiResponse;
import com.bipros.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/activities")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'SCHEDULER')")
@RequiredArgsConstructor
public class ActivityController {

  private final ActivityService activityService;
  private final GlobalChangeService globalChangeService;

  @PostMapping
  public ResponseEntity<ApiResponse<ActivityResponse>> createActivity(
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateActivityRequest request) {
    ActivityResponse response = activityService.createActivity(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<ActivityResponse>>> listActivities(
      @PathVariable UUID projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "sortOrder") String sortBy,
      @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
    PagedResponse<ActivityResponse> response = activityService.listActivities(projectId, pageable);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{activityId}")
  public ResponseEntity<ApiResponse<ActivityResponse>> getActivity(
      @PathVariable UUID projectId,
      @PathVariable UUID activityId) {
    ActivityResponse response = activityService.getActivity(activityId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PutMapping("/{activityId}")
  public ResponseEntity<ApiResponse<ActivityResponse>> updateActivity(
      @PathVariable UUID projectId,
      @PathVariable UUID activityId,
      @Valid @RequestBody UpdateActivityRequest request) {
    ActivityResponse response = activityService.updateActivity(activityId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{activityId}")
  public ResponseEntity<Void> deleteActivity(
      @PathVariable UUID projectId,
      @PathVariable UUID activityId) {
    activityService.deleteActivity(activityId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{activityId}/progress")
  public ResponseEntity<ApiResponse<ActivityResponse>> updateProgress(
      @PathVariable UUID projectId,
      @PathVariable UUID activityId,
      @RequestParam Double percentComplete,
      @RequestParam(required = false) LocalDate actualStartDate,
      @RequestParam(required = false) LocalDate actualFinishDate) {
    ActivityResponse response = activityService.updateProgress(activityId, percentComplete,
        actualStartDate, actualFinishDate);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PutMapping("/apply-actuals")
  public ResponseEntity<ApiResponse<Void>> applyActuals(
      @PathVariable UUID projectId,
      @Valid @RequestBody ApplyActualsRequest request) {
    activityService.applyActuals(projectId, request.dataDate());
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  @PostMapping("/global-change")
  public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> applyGlobalChange(
      @PathVariable UUID projectId,
      @Valid @RequestBody GlobalChangeRequest request) {
    int updatedCount = globalChangeService.applyGlobalChange(projectId, request);
    java.util.Map<String, Object> result = java.util.Map.of("updatedCount", updatedCount);
    return ResponseEntity.ok(ApiResponse.ok(result));
  }
}
