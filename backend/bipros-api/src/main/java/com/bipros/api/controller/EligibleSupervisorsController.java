package com.bipros.api.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Powers the DPR form's supervisor dropdown (Phase 7.2). Returns every active LABOR resource,
 * scoped to those allocated to the project via {@code ResourceAssignment}; if the project has
 * zero allocations the endpoint relaxes to the project-agnostic list so a fresh project isn't
 * presented with an empty dropdown. The role name is included in each response option so the
 * UI can show e.g. "T. Swamy (Supervisor)" and users pick supervisor-class workers visually.
 *
 * <p>Free-text "Other" entries on the DPR form leave {@code supervisorResourceId} null and
 * are not returned here.
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/eligible-supervisors")
@PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','PROGRAMME_MANAGER','SITE_SUPERVISOR','TEAM_MEMBER','VIEWER')")
@RequiredArgsConstructor
public class EligibleSupervisorsController {

  private static final String LABOR_TYPE_CODE = "LABOR";

  private final ResourceRepository resourceRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;

  public record EligibleSupervisorResponse(
      UUID id,
      String code,
      String name,
      String roleCode,
      String roleName
  ) {}

  @GetMapping
  @Transactional(readOnly = true)
  public ResponseEntity<ApiResponse<List<EligibleSupervisorResponse>>> list(
      @PathVariable UUID projectId) {

    // All active Labor resources — role.name is shown in each option label so users can still
    // pick supervisor-class workers (Foreman / Supervisor / Charge Hand) by visual scan.
    List<Resource> eligible = resourceRepository.findByResourceType_CodeAndStatus(
        LABOR_TYPE_CODE, ResourceStatus.ACTIVE);

    Set<UUID> projectResourceIds = resourceAssignmentRepository.findByProjectId(projectId).stream()
        .map(ra -> ra.getResourceId())
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());

    List<Resource> scoped = eligible.stream()
        .filter(r -> projectResourceIds.contains(r.getId()))
        .toList();

    List<Resource> source = scoped.isEmpty() ? eligible : scoped;

    List<EligibleSupervisorResponse> response = source.stream()
        .sorted((a, b) -> {
          int n = a.getName().compareToIgnoreCase(b.getName());
          return n != 0 ? n : a.getCode().compareToIgnoreCase(b.getCode());
        })
        .map(r -> new EligibleSupervisorResponse(
            r.getId(),
            r.getCode(),
            r.getName(),
            r.getRole() != null ? r.getRole().getCode() : null,
            r.getRole() != null ? r.getRole().getName() : null))
        .toList();

    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
