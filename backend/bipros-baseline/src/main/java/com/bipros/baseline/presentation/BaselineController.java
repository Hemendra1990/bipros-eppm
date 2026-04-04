package com.bipros.baseline.presentation;

import com.bipros.baseline.application.dto.BaselineDetailResponse;
import com.bipros.baseline.application.dto.BaselineResponse;
import com.bipros.baseline.application.dto.BaselineVarianceResponse;
import com.bipros.baseline.application.dto.CreateBaselineRequest;
import com.bipros.baseline.application.dto.ScheduleComparisonResponse;
import com.bipros.baseline.application.service.BaselineService;
import com.bipros.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/baselines")
@RequiredArgsConstructor
public class BaselineController {

  private final BaselineService baselineService;

  @PostMapping
  public ResponseEntity<ApiResponse<BaselineResponse>> createBaseline(
      @PathVariable UUID projectId, @Valid @RequestBody CreateBaselineRequest request) {
    BaselineResponse response = baselineService.createBaseline(projectId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<BaselineResponse>>> listBaselines(
      @PathVariable UUID projectId) {
    List<BaselineResponse> response = baselineService.listBaselines(projectId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{baselineId}")
  public ResponseEntity<ApiResponse<BaselineDetailResponse>> getBaseline(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    BaselineDetailResponse response = baselineService.getBaseline(baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{baselineId}")
  public ResponseEntity<Void> deleteBaseline(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    baselineService.deleteBaseline(baselineId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{baselineId}/variance")
  public ResponseEntity<ApiResponse<List<BaselineVarianceResponse>>> getVariance(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    List<BaselineVarianceResponse> response =
        baselineService.getVariance(projectId, baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{baselineId}/schedule-comparison")
  public ResponseEntity<ApiResponse<List<ScheduleComparisonResponse>>> getScheduleComparison(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    List<ScheduleComparisonResponse> response =
        baselineService.getScheduleComparison(projectId, baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
