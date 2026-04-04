package com.bipros.scheduling.api;

import com.bipros.scheduling.application.dto.ScheduleHealthResponse;
import com.bipros.scheduling.application.service.ScheduleHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
@Slf4j
@RequiredArgsConstructor
public class ScheduleHealthController {

  private final ScheduleHealthService scheduleHealthService;

  @GetMapping("/{projectId}/schedule-health")
  public ResponseEntity<ScheduleHealthResponse> getScheduleHealth(@PathVariable UUID projectId) {
    log.debug("GET /v1/projects/{}/schedule-health", projectId);
    return ResponseEntity.ok(scheduleHealthService.getLatestHealth(projectId));
  }
}
