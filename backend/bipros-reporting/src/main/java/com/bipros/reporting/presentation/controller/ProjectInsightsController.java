package com.bipros.reporting.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Project-scoped read-only insights that back the Operational / project-detail dashboards.
 * Uses {@code /v1/projects/{id}/*} (no /reports prefix) because the UI treats these as
 * live project data, not on-demand report generation.
 */
@RestController
@RequestMapping("/v1/projects/{projectId}")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VIEWER')")
@RequiredArgsConstructor
public class ProjectInsightsController {

  private final WbsNodeRepository wbsNodeRepository;

  public record WbsProgressRow(
      String wbsCode,
      String wbsName,
      Integer level,
      double plannedPct,
      double actualPct,
      double variancePct) {}

  @GetMapping("/wbs-progress")
  public ApiResponse<List<WbsProgressRow>> getWbsProgress(@PathVariable UUID projectId) {
    List<WbsNode> nodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
    LocalDate today = LocalDate.now();

    List<WbsProgressRow> rows = new ArrayList<>(nodes.size());
    for (WbsNode n : nodes) {
      double planned = computePlannedPct(today, n.getPlannedStart(), n.getPlannedFinish());
      double actual = n.getSummaryPercentComplete() != null ? n.getSummaryPercentComplete() : 0.0;
      double variance = actual - planned;
      rows.add(
          new WbsProgressRow(n.getCode(), n.getName(), n.getWbsLevel(), planned, actual, variance));
    }
    return ApiResponse.ok(rows);
  }

  private static double computePlannedPct(LocalDate today, LocalDate start, LocalDate finish) {
    if (start == null || finish == null) return 0.0;
    if (!today.isAfter(start)) return 0.0;
    if (!today.isBefore(finish)) return 100.0;
    long total = ChronoUnit.DAYS.between(start, finish);
    if (total <= 0) return 100.0;
    long elapsed = ChronoUnit.DAYS.between(start, today);
    return Math.max(0.0, Math.min(100.0, (elapsed * 100.0) / total));
  }
}
