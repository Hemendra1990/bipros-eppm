package com.bipros.risk.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.event.RiskAssessedEvent;
import com.bipros.common.event.RiskClosedEvent;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.risk.application.dto.CreateRiskRequest;
import com.bipros.risk.application.dto.CreateRiskResponseRequest;
import com.bipros.risk.application.dto.RiskActivityAssignmentDto;
import com.bipros.risk.application.dto.RiskAnalysisQuality;
import com.bipros.risk.application.dto.RiskResponseDto;
import com.bipros.risk.application.dto.RiskSummary;
import com.bipros.risk.application.dto.UpdateRiskRequest;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskActivityAssignment;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.model.RiskResponse;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskType;
import com.bipros.risk.domain.repository.RiskActivityAssignmentRepository;
import com.bipros.risk.domain.repository.RiskCategoryMasterRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RiskService {

    private final RiskRepository riskRepository;
    private final RiskResponseRepository riskResponseRepository;
    private final RiskQualityService riskQualityService;
    private final AuditService auditService;
    private final ProjectAccessGuard projectAccess;
    private final ApplicationEventPublisher eventPublisher;
    private final RiskCategoryMasterRepository riskCategoryRepository;
    private final RiskActivityAssignmentRepository assignmentRepository;
    private final ActivityRepository activityRepository;
    private final RiskScoringMatrixService matrixService;
    private final RiskExposureService exposureService;

    // ── Risk CRUD ─────────────────────────────────────────────────────────

    public RiskSummary createRisk(UUID projectId, CreateRiskRequest request) {
        projectAccess.requireEdit(projectId);
        String title = request.requiredTitle();
        if (title == null) {
            throw new BusinessRuleException("TITLE_REQUIRED", "Risk title (or name) is required");
        }

        // Ensure project has a scoring matrix
        matrixService.ensureMatrixExists(projectId);

        Risk risk = new Risk();
        risk.setProjectId(projectId);
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            code = String.format("RISK-%04d", riskRepository.maxRiskCodeNumber(projectId) + 1);
        }
        risk.setCode(code);
        risk.setTitle(title);
        risk.setDescription(request.getDescription());
        risk.setCategory(resolveCategory(request));
        risk.setProbability(request.getProbability());
        risk.setImpact(request.getImpact());
        risk.setImpactCost(request.getImpactCost());
        risk.setImpactSchedule(request.getImpactSchedule());

        // P6-style risk type (replaces isOpportunity)
        if (request.getRiskType() != null) {
            risk.setRiskType(request.getRiskType());
        }

        if (request.getStatus() != null) risk.setStatus(request.getStatus());
        risk.setOwnerId(request.getOwnerId());
        risk.setIdentifiedDate(request.getIdentifiedDate());
        risk.setIdentifiedById(request.getIdentifiedById());
        risk.setDueDate(request.getDueDate());
        risk.setAffectedActivities(request.getAffectedActivities());
        risk.setCostImpact(request.getCostImpact());
        risk.setScheduleImpactDays(request.getScheduleImpactDays());
        risk.setSortOrder(request.getSortOrder());

        // P6 Response strategy
        risk.setResponseType(request.getResponseType());
        risk.setResponseDescription(request.getResponseDescription());

        // P6 Post-response impact
        risk.setPostResponseProbability(request.getPostResponseProbability());
        risk.setPostResponseImpactCost(request.getPostResponseImpactCost());
        risk.setPostResponseImpactSchedule(request.getPostResponseImpactSchedule());

        // P6 Descriptive fields
        risk.setCause(request.getCause());
        risk.setEffect(request.getEffect());
        risk.setNotes(request.getNotes());

        // Calculate scores using matrix
        calculateScores(risk, projectId);

        Risk saved = riskRepository.save(risk);
        auditService.logCreate("Risk", saved.getId(), saved);
        eventPublisher.publishEvent(new RiskAssessedEvent(saved.getProjectId(), saved.getId()));

        RiskSummary summary = mapToSummary(saved);
        summary.setAnalysisQuality(riskQualityService.assess(saved, Collections.emptyList()));
        return summary;
    }

    public RiskSummary updateRisk(UUID projectId, UUID riskId, UpdateRiskRequest request) {
        projectAccess.requireEdit(projectId);
        Risk risk = loadProjectRisk(projectId, riskId);
        RiskStatus previousStatus = risk.getStatus();

        auditService.logUpdate("Risk", riskId, "title", risk.getTitle(), request.getTitle());

        // PATCH-like semantics: only overwrite when non-null
        if (request.getTitle() != null && !request.getTitle().isBlank()) risk.setTitle(request.getTitle());
        if (request.getDescription() != null) risk.setDescription(request.getDescription());
        if (request.getStatus() != null) risk.setStatus(request.getStatus());
        if (request.getRiskType() != null) risk.setRiskType(request.getRiskType());
        if (request.getProbability() != null) risk.setProbability(request.getProbability());
        if (request.getImpactCost() != null) risk.setImpactCost(request.getImpactCost());
        if (request.getImpactSchedule() != null) risk.setImpactSchedule(request.getImpactSchedule());
        if (request.getOwnerId() != null) risk.setOwnerId(request.getOwnerId());
        if (request.getIdentifiedDate() != null) risk.setIdentifiedDate(request.getIdentifiedDate());
        if (request.getIdentifiedById() != null) risk.setIdentifiedById(request.getIdentifiedById());
        if (request.getDueDate() != null) risk.setDueDate(request.getDueDate());
        if (request.getAffectedActivities() != null) risk.setAffectedActivities(request.getAffectedActivities());
        if (request.getCostImpact() != null) risk.setCostImpact(request.getCostImpact());
        if (request.getScheduleImpactDays() != null) risk.setScheduleImpactDays(request.getScheduleImpactDays());
        if (request.getSortOrder() != null) risk.setSortOrder(request.getSortOrder());

        // P6 Response strategy
        if (request.getResponseType() != null) risk.setResponseType(request.getResponseType());
        if (request.getResponseDescription() != null) risk.setResponseDescription(request.getResponseDescription());

        // P6 Post-response impact
        if (request.getPostResponseProbability() != null) risk.setPostResponseProbability(request.getPostResponseProbability());
        if (request.getPostResponseImpactCost() != null) risk.setPostResponseImpactCost(request.getPostResponseImpactCost());
        if (request.getPostResponseImpactSchedule() != null) risk.setPostResponseImpactSchedule(request.getPostResponseImpactSchedule());

        // P6 Descriptive fields
        if (request.getCause() != null) risk.setCause(request.getCause());
        if (request.getEffect() != null) risk.setEffect(request.getEffect());
        if (request.getNotes() != null) risk.setNotes(request.getNotes());

        // Category
        RiskCategoryMaster newCategory = resolveCategoryIfSupplied(request.getCategoryId());
        if (newCategory != null) risk.setCategory(newCategory);

        // Recalculate scores
        calculateScores(risk, projectId);

        Risk updated = riskRepository.save(risk);
        eventPublisher.publishEvent(new RiskAssessedEvent(updated.getProjectId(), updated.getId()));
        if (isTerminal(updated.getStatus()) && !isTerminal(previousStatus)) {
            eventPublisher.publishEvent(new RiskClosedEvent(
                    updated.getProjectId(), updated.getId(), Instant.now(), null));
        }
        List<RiskResponse> responses = riskResponseRepository.findByRiskId(riskId);
        RiskSummary summary = mapToSummary(updated);
        summary.setAnalysisQuality(riskQualityService.assess(updated, responses));
        return summary;
    }

    public void deleteRisk(UUID projectId, UUID riskId) {
        projectAccess.requireEdit(projectId);
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }

        // Delete assignments and responses first
        assignmentRepository.findByRiskId(riskId).forEach(a -> assignmentRepository.delete(a));
        riskResponseRepository.deleteAll(riskResponseRepository.findByRiskId(riskId));
        riskRepository.delete(risk);
        auditService.logDelete("Risk", riskId);
        eventPublisher.publishEvent(new RiskClosedEvent(projectId, riskId, Instant.now(), null));
    }

    private static boolean isTerminal(RiskStatus status) {
        return status == RiskStatus.CLOSED || status == RiskStatus.RESOLVED;
    }

    @Transactional(readOnly = true)
    public RiskSummary getRisk(UUID projectId, UUID riskId) {
        projectAccess.requireRead(projectId);
        Risk risk = loadProjectRisk(projectId, riskId);
        List<RiskResponse> responses = riskResponseRepository.findByRiskId(riskId);
        RiskSummary summary = mapToSummary(risk);
        summary.setAnalysisQuality(riskQualityService.assess(risk, responses));

        // Include assigned activities
        summary.setAssignedActivities(getAssignedActivities(projectId, riskId));

        return summary;
    }

    @Transactional(readOnly = true)
    public List<RiskSummary> listRisks(UUID projectId, RiskStatus status) {
        projectAccess.requireRead(projectId);
        List<Risk> risks;
        if (status != null) {
            risks = riskRepository.findByProjectIdAndStatus(projectId, status);
        } else {
            risks = riskRepository.findByProjectId(projectId);
        }
        if (risks.isEmpty()) return List.of();

        List<UUID> riskIds = risks.stream().map(Risk::getId).toList();
        Map<UUID, List<RiskResponse>> responsesByRisk = riskResponseRepository
            .findByRiskIdIn(riskIds).stream()
            .collect(Collectors.groupingBy(RiskResponse::getRiskId));

        // Batch fetch assignments for all risks
        Map<UUID, List<RiskActivityAssignment>> assignmentsByRisk = assignmentRepository
            .findByProjectId(projectId).stream()
            .collect(Collectors.groupingBy(RiskActivityAssignment::getRiskId));

        return risks.stream().map(r -> {
            RiskSummary summary = mapToSummary(r);
            summary.setAnalysisQuality(riskQualityService.assess(
                r, responsesByRisk.getOrDefault(r.getId(), Collections.emptyList())));

            // Map assignments to DTOs (without activity details for list view)
            List<RiskActivityAssignment> assignments = assignmentsByRisk
                .getOrDefault(r.getId(), Collections.emptyList());
            summary.setAssignedActivities(assignments.stream()
                .map(a -> RiskActivityAssignmentDto.builder()
                    .id(a.getId())
                    .riskId(a.getRiskId())
                    .activityId(a.getActivityId())
                    .projectId(a.getProjectId())
                    .build())
                .toList());

            return summary;
        }).collect(Collectors.toList());
    }

    // ── Activity Assignment ───────────────────────────────────────────────

    public RiskActivityAssignmentDto addActivityToRisk(UUID projectId, UUID riskId, UUID activityId) {
        projectAccess.requireEdit(projectId);
        Risk risk = loadProjectRisk(projectId, riskId);

        // Validate activity exists and belongs to same project
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));
        if (!activity.getProjectId().equals(projectId)) {
            throw new BusinessRuleException("ACTIVITY_PROJECT_MISMATCH",
                "Activity does not belong to the same project as the risk");
        }

        // Check for duplicate
        if (assignmentRepository.existsByRiskIdAndActivityId(riskId, activityId)) {
            throw new BusinessRuleException("DUPLICATE_ASSIGNMENT",
                "Activity is already assigned to this risk");
        }

        RiskActivityAssignment assignment = new RiskActivityAssignment();
        assignment.setRiskId(riskId);
        assignment.setActivityId(activityId);
        assignment.setProjectId(projectId);
        assignmentRepository.save(assignment);

        // Recalculate exposure dates and costs
        exposureService.recalculateAll(riskId);

        auditService.logCreate("RiskActivityAssignment", assignment.getId(), assignment);

        return RiskActivityAssignmentDto.builder()
            .id(assignment.getId())
            .riskId(riskId)
            .activityId(activityId)
            .projectId(projectId)
            .activityCode(activity.getCode())
            .activityName(activity.getName())
            .activityStartDate(activity.getPlannedStartDate())
            .activityFinishDate(activity.getPlannedFinishDate())
            .build();
    }

    public void removeActivityFromRisk(UUID projectId, UUID riskId, UUID activityId) {
        projectAccess.requireEdit(projectId);
        loadProjectRisk(projectId, riskId); // Validate ownership

        // Look up the assignment first so we can audit by its UUID before deleting.
        UUID assignmentId = assignmentRepository
            .findByRiskIdAndActivityId(riskId, activityId)
            .map(a -> a.getId())
            .orElse(null);

        assignmentRepository.deleteByRiskIdAndActivityId(riskId, activityId);

        exposureService.recalculateAll(riskId);

        if (assignmentId != null) {
            auditService.logDelete("RiskActivityAssignment", assignmentId);
        }
    }

    @Transactional(readOnly = true)
    public List<RiskActivityAssignmentDto> getAssignedActivities(UUID projectId, UUID riskId) {
        loadProjectRisk(projectId, riskId); // Validate ownership

        List<RiskActivityAssignment> assignments = assignmentRepository.findByRiskId(riskId);
        if (assignments.isEmpty()) return List.of();

        List<UUID> activityIds = assignments.stream()
            .map(RiskActivityAssignment::getActivityId)
            .toList();

        Map<UUID, Activity> activityMap = activityRepository.findByIdIn(activityIds).stream()
            .collect(Collectors.toMap(Activity::getId, a -> a));

        return assignments.stream().map(a -> {
            Activity activity = activityMap.get(a.getActivityId());
            return RiskActivityAssignmentDto.builder()
                .id(a.getId())
                .riskId(a.getRiskId())
                .activityId(a.getActivityId())
                .projectId(a.getProjectId())
                .activityCode(activity != null ? activity.getCode() : null)
                .activityName(activity != null ? activity.getName() : null)
                .activityStartDate(activity != null ? activity.getPlannedStartDate() : null)
                .activityFinishDate(activity != null ? activity.getPlannedFinishDate() : null)
                .build();
        }).toList();
    }

    // ── Analysis Quality ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RiskAnalysisQuality assessQuality(UUID projectId, UUID riskId) {
        Risk risk = loadProjectRisk(projectId, riskId);
        return riskQualityService.assess(risk, riskResponseRepository.findByRiskId(riskId));
    }

    // ── Matrix & Exposure ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, List<RiskSummary>> getRiskMatrix(UUID projectId) {
        List<Risk> risks = riskRepository.findByProjectId(projectId);
        Map<String, List<RiskSummary>> matrix = new HashMap<>();

        for (Risk risk : risks) {
            if (risk.getProbability() != null && risk.getImpactCost() != null) {
                String key = risk.getProbability().name() + "_" + risk.getImpactCost();
                matrix.computeIfAbsent(key, k -> new java.util.ArrayList<>())
                    .add(mapToSummary(risk));
            }
        }

        return matrix;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateRiskExposure(UUID projectId) {
        List<Risk> risks = riskRepository.findByProjectId(projectId);
        return risks.stream()
            .filter(r -> !RiskStatus.CLOSED.equals(r.getStatus()) && !RiskStatus.RESOLVED.equals(r.getStatus()))
            .filter(r -> r.getPreResponseExposureCost() != null)
            .map(Risk::getPreResponseExposureCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Response management (existing) ────────────────────────────────────

    public RiskResponseDto addResponse(UUID projectId, UUID riskId, CreateRiskResponseRequest request) {
        projectAccess.requireEdit(projectId);
        loadProjectRisk(projectId, riskId);

        RiskResponse response = new RiskResponse();
        response.setRiskId(riskId);
        response.setResponseType(request.getResponseType());
        response.setDescription(request.getDescription());
        response.setResponsibleId(request.getResponsibleId());
        response.setPlannedDate(request.getPlannedDate());
        response.setActualDate(request.getActualDate());
        response.setEstimatedCost(request.getEstimatedCost());
        response.setActualCost(request.getActualCost());
        response.setStatus(request.getStatus());

        RiskResponse saved = riskResponseRepository.save(response);
        auditService.logCreate("RiskResponse", saved.getId(), saved);
        eventPublisher.publishEvent(new RiskAssessedEvent(projectId, riskId));
        return mapToResponseDto(saved);
    }

    public RiskResponseDto updateResponse(UUID projectId, UUID riskId, UUID responseId, CreateRiskResponseRequest request) {
        projectAccess.requireEdit(projectId);
        loadProjectRisk(projectId, riskId);

        RiskResponse response = riskResponseRepository.findById(responseId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskResponse", responseId));
        if (!response.getRiskId().equals(riskId)) {
            throw new ResourceNotFoundException("RiskResponse", responseId);
        }

        response.setResponseType(request.getResponseType());
        response.setDescription(request.getDescription());
        response.setResponsibleId(request.getResponsibleId());
        response.setPlannedDate(request.getPlannedDate());
        response.setActualDate(request.getActualDate());
        response.setEstimatedCost(request.getEstimatedCost());
        response.setActualCost(request.getActualCost());
        response.setStatus(request.getStatus());

        RiskResponse updated = riskResponseRepository.save(response);
        auditService.logUpdate("RiskResponse", responseId, "riskResponse", null, updated);
        eventPublisher.publishEvent(new RiskAssessedEvent(projectId, riskId));
        return mapToResponseDto(updated);
    }

    public void deleteResponse(UUID projectId, UUID riskId, UUID responseId) {
        projectAccess.requireEdit(projectId);
        loadProjectRisk(projectId, riskId);
        RiskResponse response = riskResponseRepository.findById(responseId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskResponse", responseId));
        if (!response.getRiskId().equals(riskId)) {
            throw new ResourceNotFoundException("RiskResponse", responseId);
        }
        riskResponseRepository.delete(response);
        auditService.logDelete("RiskResponse", responseId);
        eventPublisher.publishEvent(new RiskAssessedEvent(projectId, riskId));
    }

    @Transactional(readOnly = true)
    public List<RiskResponseDto> getResponses(UUID projectId, UUID riskId) {
        projectAccess.requireRead(projectId);
        loadProjectRisk(projectId, riskId);
        return riskResponseRepository.findByRiskId(riskId).stream()
            .map(this::mapToResponseDto)
            .collect(Collectors.toList());
    }

    // ── Public summary mapper ─────────────────────────────────────────────

    public RiskSummary toSummary(Risk risk) {
        return mapToSummary(risk);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Recompute pre- and post-response scores using the project's scoring matrix and method.
     * Public so other services in the module (e.g., template copy) can apply the same logic.
     */
    public void calculateScores(Risk risk, UUID projectId) {
        if (risk.getProbability() != null) {
            Integer score = matrixService.computeCompositeScore(
                projectId,
                risk.getProbability().getValue(),
                risk.getImpactCost(),
                risk.getImpactSchedule());
            risk.applyPreResponseScore(score);
        }
        if (risk.getPostResponseProbability() != null) {
            Integer score = matrixService.computeCompositeScore(
                projectId,
                risk.getPostResponseProbability().getValue(),
                risk.getPostResponseImpactCost(),
                risk.getPostResponseImpactSchedule());
            risk.applyPostResponseScore(score);
        }
    }

    private Risk loadProjectRisk(UUID projectId, UUID riskId) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));
        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }
        return risk;
    }

    private RiskSummary mapToSummary(Risk risk) {
        return RiskSummary.builder()
            .id(risk.getId())
            .code(risk.getCode())
            .title(risk.getTitle())
            .description(risk.getDescription())
            .category(RiskCategoryMasterService.toSummary(risk.getCategory()))
            .status(risk.getStatus())
            .riskType(risk.getRiskType())
            .probability(risk.getProbability())
            .impact(risk.getImpact())
            .riskScore(risk.getRiskScore())
            .ownerId(risk.getOwnerId())
            .identifiedDate(risk.getIdentifiedDate())
            .identifiedById(risk.getIdentifiedById())
            .dueDate(risk.getDueDate())
            .affectedActivities(risk.getAffectedActivities())
            .costImpact(risk.getCostImpact())
            .scheduleImpactDays(risk.getScheduleImpactDays())
            .sortOrder(risk.getSortOrder())
            // IC-PMS M7 extensions
            .rag(risk.getRag())
            .trend(risk.getTrend())
            .isOpportunity(risk.isOpportunity())
            .residualRiskScore(risk.getResidualRiskScore())
            .impactCost(risk.getImpactCost())
            .impactSchedule(risk.getImpactSchedule())
            // P6 Exposure
            .exposureStartDate(risk.getExposureStartDate())
            .exposureFinishDate(risk.getExposureFinishDate())
            .preResponseExposureCost(risk.getPreResponseExposureCost())
            .postResponseExposureCost(risk.getPostResponseExposureCost())
            // P6 Response strategy
            .responseType(risk.getResponseType())
            .responseDescription(risk.getResponseDescription())
            // P6 Post-response
            .postResponseProbability(risk.getPostResponseProbability())
            .postResponseImpactCost(risk.getPostResponseImpactCost())
            .postResponseImpactSchedule(risk.getPostResponseImpactSchedule())
            .postResponseRiskScore(risk.getPostResponseRiskScore())
            // P6 Descriptive
            .cause(risk.getCause())
            .effect(risk.getEffect())
            .notes(risk.getNotes())
            .build();
    }

    private RiskCategoryMaster resolveCategory(CreateRiskRequest request) {
        if (request.getCategoryId() != null) {
            return loadActiveCategoryById(request.getCategoryId());
        }
        if (request.getLegacyCategoryCode() != null && !request.getLegacyCategoryCode().isBlank()) {
            return loadActiveCategoryByCode(request.getLegacyCategoryCode());
        }
        return null;
    }

    private RiskCategoryMaster resolveCategoryIfSupplied(UUID categoryId) {
        if (categoryId != null) {
            return loadActiveCategoryById(categoryId);
        }
        return null;
    }

    private RiskCategoryMaster loadActiveCategoryById(UUID categoryId) {
        RiskCategoryMaster cat = riskCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryMaster", categoryId));
        if (!Boolean.TRUE.equals(cat.getActive())) {
            throw new BusinessRuleException("RISK_CATEGORY_INACTIVE",
                "Risk category '" + cat.getCode() + "' is inactive");
        }
        return cat;
    }

    private RiskCategoryMaster loadActiveCategoryByCode(String code) {
        String normalized = code.trim().toUpperCase();
        RiskCategoryMaster cat = riskCategoryRepository.findByCode(normalized)
            .orElseThrow(() -> new BusinessRuleException("INVALID_RISK_CATEGORY",
                "Unknown risk category code '" + normalized + "'"));
        if (!Boolean.TRUE.equals(cat.getActive())) {
            throw new BusinessRuleException("RISK_CATEGORY_INACTIVE",
                "Risk category '" + cat.getCode() + "' is inactive");
        }
        return cat;
    }

    private RiskResponseDto mapToResponseDto(RiskResponse response) {
        return RiskResponseDto.builder()
            .id(response.getId())
            .riskId(response.getRiskId())
            .responseType(response.getResponseType())
            .description(response.getDescription())
            .responsibleId(response.getResponsibleId())
            .plannedDate(response.getPlannedDate())
            .actualDate(response.getActualDate())
            .estimatedCost(response.getEstimatedCost())
            .actualCost(response.getActualCost())
            .status(response.getStatus())
            .build();
    }
}
