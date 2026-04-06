package com.bipros.contract.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.ProcurementPlanRequest;
import com.bipros.contract.application.dto.ProcurementPlanResponse;
import com.bipros.contract.domain.model.ProcurementPlan;
import com.bipros.contract.domain.repository.ProcurementPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProcurementPlanService {

    private final ProcurementPlanRepository procurementPlanRepository;
    private final AuditService auditService;

    public ProcurementPlanResponse create(ProcurementPlanRequest request) {
        log.info("Creating procurement plan with code: {}", request.planCode());

        ProcurementPlan plan = new ProcurementPlan();
        plan.setProjectId(request.projectId());
        plan.setWbsNodeId(request.wbsNodeId());
        plan.setPlanCode(request.planCode());
        plan.setDescription(request.description());
        plan.setProcurementMethod(request.procurementMethod());
        plan.setEstimatedValue(request.estimatedValue());
        plan.setCurrency(request.currency() != null ? request.currency() : "INR");
        plan.setTargetNitDate(request.targetNitDate());
        plan.setTargetAwardDate(request.targetAwardDate());
        plan.setApprovalLevel(request.approvalLevel());
        plan.setApprovedBy(request.approvedBy());

        ProcurementPlan saved = procurementPlanRepository.save(plan);
        log.info("Procurement plan created with ID: {}", saved.getId());
        auditService.logCreate("ProcurementPlan", saved.getId(), toResponse(saved));

        return toResponse(saved);
    }

    public ProcurementPlanResponse getById(UUID id) {
        log.info("Fetching procurement plan with ID: {}", id);

        ProcurementPlan plan = procurementPlanRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProcurementPlan", id));

        return toResponse(plan);
    }

    public PagedResponse<ProcurementPlanResponse> listByProject(UUID projectId, Pageable pageable) {
        log.info("Listing procurement plans for project: {}", projectId);

        Page<ProcurementPlan> page = procurementPlanRepository.findByProjectId(projectId, pageable);

        return PagedResponse.of(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    public List<ProcurementPlanResponse> listByWbsNode(UUID wbsNodeId) {
        log.info("Listing procurement plans for WBS node: {}", wbsNodeId);

        return procurementPlanRepository.findByWbsNodeId(wbsNodeId).stream()
            .map(this::toResponse)
            .toList();
    }

    public ProcurementPlanResponse update(UUID id, ProcurementPlanRequest request) {
        log.info("Updating procurement plan with ID: {}", id);

        ProcurementPlan plan = procurementPlanRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProcurementPlan", id));

        plan.setDescription(request.description());
        plan.setProcurementMethod(request.procurementMethod());
        plan.setEstimatedValue(request.estimatedValue());
        plan.setCurrency(request.currency() != null ? request.currency() : plan.getCurrency());
        plan.setTargetNitDate(request.targetNitDate());
        plan.setTargetAwardDate(request.targetAwardDate());
        plan.setApprovalLevel(request.approvalLevel());

        ProcurementPlan updated = procurementPlanRepository.save(plan);
        auditService.logUpdate("ProcurementPlan", id, "plan", null, toResponse(updated));
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting procurement plan with ID: {}", id);
        procurementPlanRepository.deleteById(id);
        auditService.logDelete("ProcurementPlan", id);
    }

    private ProcurementPlanResponse toResponse(ProcurementPlan plan) {
        return new ProcurementPlanResponse(
            plan.getId(),
            plan.getProjectId(),
            plan.getWbsNodeId(),
            plan.getPlanCode(),
            plan.getDescription(),
            plan.getProcurementMethod(),
            plan.getEstimatedValue(),
            plan.getCurrency(),
            plan.getTargetNitDate(),
            plan.getTargetAwardDate(),
            plan.getStatus(),
            plan.getApprovalLevel(),
            plan.getApprovedBy(),
            plan.getApprovedAt(),
            plan.getCreatedAt(),
            plan.getUpdatedAt()
        );
    }
}
