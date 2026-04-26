package com.bipros.risk.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.risk.application.dto.CreateRiskRequest;
import com.bipros.risk.application.dto.CreateRiskResponseRequest;
import com.bipros.risk.application.dto.RiskAnalysisQuality;
import com.bipros.risk.application.dto.RiskResponseDto;
import com.bipros.risk.application.dto.RiskSummary;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskResponse;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RiskService {

    private final RiskRepository riskRepository;
    private final RiskResponseRepository riskResponseRepository;
    private final RiskQualityService riskQualityService;
    private final AuditService auditService;
    private final ProjectAccessGuard projectAccess;

    public RiskSummary createRisk(UUID projectId, CreateRiskRequest request) {
        projectAccess.requireEdit(projectId);
        String title = request.requiredTitle();
        if (title == null) {
            throw new com.bipros.common.exception.BusinessRuleException(
                "TITLE_REQUIRED", "Risk title (or name) is required");
        }
        Risk risk = new Risk();
        risk.setProjectId(projectId);
        // Code is NOT NULL; auto-generate RISK-NNNN if the caller didn't supply one
        // (mirrors the convention used by RiskTemplateService.copyToProject).
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            code = String.format("RISK-%04d", riskRepository.findByProjectId(projectId).size() + 1);
        }
        risk.setCode(code);
        risk.setTitle(title);
        risk.setDescription(request.getDescription());
        risk.setCategory(request.getCategory());
        risk.setProbability(request.getProbability());
        risk.setImpact(request.getImpact());
        risk.setImpactCost(request.getImpactCost());
        risk.setImpactSchedule(request.getImpactSchedule());
        if (request.getStatus() != null) risk.setStatus(request.getStatus());
        if (request.getIsOpportunity() != null) risk.setIsOpportunity(request.getIsOpportunity());
        risk.setOwnerId(request.getOwnerId());
        risk.setIdentifiedDate(request.getIdentifiedDate());
        risk.setDueDate(request.getDueDate());
        risk.setAffectedActivities(request.getAffectedActivities());
        risk.setCostImpact(request.getCostImpact());
        risk.setScheduleImpactDays(request.getScheduleImpactDays());
        risk.setSortOrder(request.getSortOrder());
        risk.calculateRiskScore();

        Risk saved = riskRepository.save(risk);
        auditService.logCreate("Risk", saved.getId(), saved);
        // Re-compute analysis-quality so the caller can show the badge immediately.
        RiskSummary summary = mapToSummary(saved);
        summary.setAnalysisQuality(riskQualityService.assess(
            saved, java.util.Collections.emptyList()));
        return summary;
    }

    public RiskSummary updateRisk(UUID projectId, UUID riskId, CreateRiskRequest request) {
        projectAccess.requireEdit(projectId);
        Risk risk = loadProjectRisk(projectId, riskId);

        auditService.logUpdate("Risk", riskId, "title", risk.getTitle(), request.getTitle());
        auditService.logUpdate("Risk", riskId, "description", risk.getDescription(), request.getDescription());
        auditService.logUpdate("Risk", riskId, "category", risk.getCategory(), request.getCategory());

        // PATCH-like semantics: only overwrite a field when the caller explicitly supplies
        // a non-null value. This protects partial PUTs (e.g. "just assigning an owner")
        // from wiping out the title/description/category that the frontend didn't re-send.
        // Callers that genuinely want to clear a field send a sentinel (empty string) for
        // strings or omit the field entirely to leave it untouched.
        if (request.getCode() != null && !request.getCode().isBlank()) risk.setCode(request.getCode());
        if (request.getTitle() != null && !request.getTitle().isBlank()) risk.setTitle(request.getTitle());
        if (request.getStatus() != null) risk.setStatus(request.getStatus());
        if (request.getIsOpportunity() != null) risk.setIsOpportunity(request.getIsOpportunity());
        if (request.getDescription() != null) risk.setDescription(request.getDescription());
        if (request.getCategory() != null) risk.setCategory(request.getCategory());
        if (request.getProbability() != null) risk.setProbability(request.getProbability());
        if (request.getImpact() != null) risk.setImpact(request.getImpact());
        if (request.getImpactCost() != null) risk.setImpactCost(request.getImpactCost());
        if (request.getImpactSchedule() != null) risk.setImpactSchedule(request.getImpactSchedule());
        if (request.getOwnerId() != null) risk.setOwnerId(request.getOwnerId());
        if (request.getIdentifiedDate() != null) risk.setIdentifiedDate(request.getIdentifiedDate());
        if (request.getDueDate() != null) risk.setDueDate(request.getDueDate());
        if (request.getAffectedActivities() != null) risk.setAffectedActivities(request.getAffectedActivities());
        if (request.getCostImpact() != null) risk.setCostImpact(request.getCostImpact());
        if (request.getScheduleImpactDays() != null) risk.setScheduleImpactDays(request.getScheduleImpactDays());
        // sortOrder is a primitive int (defaults to 0 in the DTO), so we can't distinguish
        // "absent" from "explicitly zero"; only overwrite when non-zero.
        if (request.getSortOrder() != 0) risk.setSortOrder(request.getSortOrder());
        risk.calculateRiskScore();

        Risk updated = riskRepository.save(risk);
        // Re-compute analysis-quality so the caller sees the fresh score in the same round-trip.
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

        riskResponseRepository.deleteAll(riskResponseRepository.findByRiskId(riskId));
        riskRepository.delete(risk);
        auditService.logDelete("Risk", riskId);
    }

    @Transactional(readOnly = true)
    public RiskSummary getRisk(UUID projectId, UUID riskId) {
        projectAccess.requireRead(projectId);
        Risk risk = loadProjectRisk(projectId, riskId);
        List<RiskResponse> responses = riskResponseRepository.findByRiskId(riskId);
        RiskSummary summary = mapToSummary(risk);
        summary.setAnalysisQuality(riskQualityService.assess(risk, responses));
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

        // Batch fetch responses for all risks in this project; group by riskId so the
        // quality assessment for each row sees its own response list without N+1 queries.
        List<UUID> riskIds = risks.stream().map(Risk::getId).toList();
        Map<UUID, List<RiskResponse>> responsesByRisk = riskResponseRepository
            .findByRiskIdIn(riskIds).stream()
            .collect(Collectors.groupingBy(RiskResponse::getRiskId));

        return risks.stream().map(r -> {
            RiskSummary summary = mapToSummary(r);
            summary.setAnalysisQuality(riskQualityService.assess(
                r, responsesByRisk.getOrDefault(r.getId(), Collections.emptyList())));
            return summary;
        }).collect(Collectors.toList());
    }

    /**
     * Standalone analysis-quality endpoint. Re-uses the same assessment that {@link
     * #listRisks} attaches to each summary; exists so clients can poll quality after
     * editing a risk without re-fetching the whole register.
     */
    @Transactional(readOnly = true)
    public RiskAnalysisQuality assessQuality(UUID projectId, UUID riskId) {
        Risk risk = loadProjectRisk(projectId, riskId);
        return riskQualityService.assess(risk, riskResponseRepository.findByRiskId(riskId));
    }

    private Risk loadProjectRisk(UUID projectId, UUID riskId) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));
        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }
        return risk;
    }

    public RiskResponseDto addResponse(UUID projectId, UUID riskId, CreateRiskResponseRequest request) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }

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
        return mapToResponseDto(saved);
    }

    public RiskResponseDto updateResponse(UUID projectId, UUID riskId, UUID responseId, CreateRiskResponseRequest request) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }

        RiskResponse response = riskResponseRepository.findById(responseId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskResponse", responseId));

        auditService.logUpdate("RiskResponse", responseId, "status", response.getStatus(), request.getStatus());
        auditService.logUpdate("RiskResponse", responseId, "description", response.getDescription(), request.getDescription());

        response.setResponseType(request.getResponseType());
        response.setDescription(request.getDescription());
        response.setResponsibleId(request.getResponsibleId());
        response.setPlannedDate(request.getPlannedDate());
        response.setActualDate(request.getActualDate());
        response.setEstimatedCost(request.getEstimatedCost());
        response.setActualCost(request.getActualCost());
        response.setStatus(request.getStatus());

        RiskResponse updated = riskResponseRepository.save(response);
        return mapToResponseDto(updated);
    }

    @Transactional(readOnly = true)
    public List<RiskResponseDto> getResponses(UUID projectId, UUID riskId) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }

        return riskResponseRepository.findByRiskId(riskId).stream()
            .map(this::mapToResponseDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, List<RiskSummary>> getRiskMatrix(UUID projectId) {
        List<Risk> risks = riskRepository.findByProjectId(projectId);
        Map<String, List<RiskSummary>> matrix = new HashMap<>();

        for (Risk risk : risks) {
            if (risk.getProbability() != null && risk.getImpact() != null) {
                String key = risk.getProbability().name() + "_" + risk.getImpact().name();
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
            .filter(r -> r.getProbability() != null && r.getCostImpact() != null)
            .map(r -> BigDecimal.valueOf(r.getProbability().getValue())
                .multiply(r.getCostImpact()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Public summary mapper. Exposed so other services in this module (e.g. {@code
     * RiskTemplateService.copyToProject}) can return the same shape the controllers do
     * without duplicating the field list.
     */
    public RiskSummary toSummary(Risk risk) {
        return mapToSummary(risk);
    }

    private RiskSummary mapToSummary(Risk risk) {
        return RiskSummary.builder()
            .id(risk.getId())
            .code(risk.getCode())
            .title(risk.getTitle())
            .description(risk.getDescription())
            .category(risk.getCategory())
            .status(risk.getStatus())
            .probability(risk.getProbability())
            .impact(risk.getImpact())
            .riskScore(risk.getRiskScore())
            .ownerId(risk.getOwnerId())
            .identifiedDate(risk.getIdentifiedDate())
            .dueDate(risk.getDueDate())
            .affectedActivities(risk.getAffectedActivities())
            .costImpact(risk.getCostImpact())
            .scheduleImpactDays(risk.getScheduleImpactDays())
            .sortOrder(risk.getSortOrder())
            // IC-PMS M7 extensions — surface persisted fields to the frontend.
            .rag(risk.getRag())
            .trend(risk.getTrend())
            .isOpportunity(risk.getIsOpportunity())
            .residualRiskScore(risk.getResidualRiskScore())
            .impactCost(risk.getImpactCost())
            .impactSchedule(risk.getImpactSchedule())
            .build();
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
