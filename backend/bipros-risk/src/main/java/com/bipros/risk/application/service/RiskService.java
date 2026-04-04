package com.bipros.risk.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.risk.application.dto.CreateRiskRequest;
import com.bipros.risk.application.dto.CreateRiskResponseRequest;
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

    public RiskSummary createRisk(UUID projectId, CreateRiskRequest request) {
        Risk risk = new Risk();
        risk.setProjectId(projectId);
        risk.setCode(request.getCode());
        risk.setTitle(request.getTitle());
        risk.setDescription(request.getDescription());
        risk.setCategory(request.getCategory());
        risk.setProbability(request.getProbability());
        risk.setImpact(request.getImpact());
        risk.setOwnerId(request.getOwnerId());
        risk.setIdentifiedDate(request.getIdentifiedDate());
        risk.setDueDate(request.getDueDate());
        risk.setAffectedActivities(request.getAffectedActivities());
        risk.setCostImpact(request.getCostImpact());
        risk.setScheduleImpactDays(request.getScheduleImpactDays());
        risk.setSortOrder(request.getSortOrder());
        risk.calculateRiskScore();

        Risk saved = riskRepository.save(risk);
        return mapToSummary(saved);
    }

    public RiskSummary updateRisk(UUID projectId, UUID riskId, CreateRiskRequest request) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }

        risk.setCode(request.getCode());
        risk.setTitle(request.getTitle());
        risk.setDescription(request.getDescription());
        risk.setCategory(request.getCategory());
        risk.setProbability(request.getProbability());
        risk.setImpact(request.getImpact());
        risk.setOwnerId(request.getOwnerId());
        risk.setIdentifiedDate(request.getIdentifiedDate());
        risk.setDueDate(request.getDueDate());
        risk.setAffectedActivities(request.getAffectedActivities());
        risk.setCostImpact(request.getCostImpact());
        risk.setScheduleImpactDays(request.getScheduleImpactDays());
        risk.setSortOrder(request.getSortOrder());
        risk.calculateRiskScore();

        Risk updated = riskRepository.save(risk);
        return mapToSummary(updated);
    }

    public void deleteRisk(UUID projectId, UUID riskId) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }

        riskResponseRepository.deleteAll(riskResponseRepository.findByRiskId(riskId));
        riskRepository.delete(risk);
    }

    public RiskSummary getRisk(UUID projectId, UUID riskId) {
        Risk risk = riskRepository.findById(riskId)
            .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", projectId);
        }

        return mapToSummary(risk);
    }

    @Transactional(readOnly = true)
    public List<RiskSummary> listRisks(UUID projectId, RiskStatus status) {
        List<Risk> risks;
        if (status != null) {
            risks = riskRepository.findByProjectIdAndStatus(projectId, status);
        } else {
            risks = riskRepository.findByProjectId(projectId);
        }
        return risks.stream().map(this::mapToSummary).collect(Collectors.toList());
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
