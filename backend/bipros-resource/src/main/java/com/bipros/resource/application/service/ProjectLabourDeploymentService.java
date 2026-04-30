package com.bipros.resource.application.service;

import com.bipros.common.event.LabourDeploymentChangedEvent;
import com.bipros.resource.application.dto.LabourCategorySummary;
import com.bipros.resource.application.dto.LabourDesignationResponse;
import com.bipros.resource.application.dto.LabourMasterDashboardSummary;
import com.bipros.resource.application.dto.ProjectLabourDeploymentRequest;
import com.bipros.resource.application.dto.ProjectLabourDeploymentResponse;
import com.bipros.resource.domain.model.LabourCategory;
import com.bipros.resource.domain.model.LabourDesignation;
import com.bipros.resource.domain.model.ProjectLabourDeployment;
import com.bipros.resource.domain.repository.LabourDesignationRepository;
import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectLabourDeploymentService {

    private final ProjectLabourDeploymentRepository deploymentRepo;
    private final LabourDesignationRepository designationRepo;
    private final LabourDesignationService designationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProjectLabourDeploymentResponse create(UUID projectId, ProjectLabourDeploymentRequest req) {
        LabourDesignation designation = designationRepo.findById(req.designationId())
            .orElseThrow(() -> new EntityNotFoundException(
                "LabourDesignation not found: " + req.designationId()));
        if (deploymentRepo.existsByProjectIdAndDesignationId(projectId, req.designationId())) {
            throw new IllegalStateException(
                "Deployment already exists for designation " + designation.getCode());
        }
        ProjectLabourDeployment dep = ProjectLabourDeployment.builder()
            .projectId(projectId)
            .designationId(req.designationId())
            .workerCount(req.workerCount())
            .actualDailyRate(req.actualDailyRate())
            .notes(req.notes())
            .build();
        ProjectLabourDeployment saved = deploymentRepo.save(dep);
        eventPublisher.publishEvent(new LabourDeploymentChangedEvent(
            projectId, saved.getId(), saved.getDesignationId(),
            LabourDeploymentChangedEvent.ChangeType.CREATED));
        return toResponse(saved, designation);
    }

    @Transactional
    public ProjectLabourDeploymentResponse update(UUID projectId, UUID deploymentId,
                                                  ProjectLabourDeploymentRequest req) {
        ProjectLabourDeployment existing = deploymentRepo.findById(deploymentId)
            .orElseThrow(() -> new EntityNotFoundException("Deployment not found: " + deploymentId));
        if (!existing.getProjectId().equals(projectId)) {
            throw new EntityNotFoundException("Deployment not found in project: " + deploymentId);
        }
        existing.setWorkerCount(req.workerCount());
        existing.setActualDailyRate(req.actualDailyRate());
        existing.setNotes(req.notes());
        LabourDesignation d = designationRepo.findById(existing.getDesignationId())
            .orElseThrow(() -> new EntityNotFoundException(
                "LabourDesignation not found: " + existing.getDesignationId()));
        ProjectLabourDeployment saved = deploymentRepo.save(existing);
        eventPublisher.publishEvent(new LabourDeploymentChangedEvent(
            projectId, saved.getId(), saved.getDesignationId(),
            LabourDeploymentChangedEvent.ChangeType.UPDATED));
        return toResponse(saved, d);
    }

    @Transactional
    public void delete(UUID projectId, UUID deploymentId) {
        ProjectLabourDeployment existing = deploymentRepo.findById(deploymentId)
            .orElseThrow(() -> new EntityNotFoundException("Deployment not found: " + deploymentId));
        if (!existing.getProjectId().equals(projectId)) {
            throw new EntityNotFoundException("Deployment not found in project: " + deploymentId);
        }
        deploymentRepo.delete(existing);
        eventPublisher.publishEvent(new LabourDeploymentChangedEvent(
            projectId, existing.getId(), existing.getDesignationId(),
            LabourDeploymentChangedEvent.ChangeType.DELETED));
    }

    @Transactional(readOnly = true)
    public List<ProjectLabourDeploymentResponse> listForProject(UUID projectId) {
        List<ProjectLabourDeployment> deps = deploymentRepo.findAllByProjectId(projectId);
        Map<UUID, LabourDesignation> byId = loadDesignations(deps);
        return deps.stream()
            .map(dep -> toResponse(dep, byId.get(dep.getDesignationId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public LabourMasterDashboardSummary dashboard(UUID projectId) {
        List<ProjectLabourDeployment> deps = deploymentRepo.findAllByProjectId(projectId);
        Map<UUID, LabourDesignation> byId = loadDesignations(deps);

        int totalWorkforce = 0;
        BigDecimal dailyPayroll = BigDecimal.ZERO;
        int omani = 0, expat = 0, omaniOrExpat = 0;
        Map<LabourCategory, List<DeployRow>> byCat = new EnumMap<>(LabourCategory.class);

        for (ProjectLabourDeployment dep : deps) {
            LabourDesignation d = byId.get(dep.getDesignationId());
            if (d == null) continue;
            BigDecimal rate = effectiveRate(dep, d);
            BigDecimal cost = rate.multiply(BigDecimal.valueOf(dep.getWorkerCount()));
            totalWorkforce += dep.getWorkerCount();
            dailyPayroll = dailyPayroll.add(cost);
            switch (d.getNationality()) {
                case OMANI -> omani += dep.getWorkerCount();
                case EXPAT -> expat += dep.getWorkerCount();
                case OMANI_OR_EXPAT -> omaniOrExpat += dep.getWorkerCount();
            }
            byCat.computeIfAbsent(d.getCategory(), k -> new ArrayList<>())
                 .add(new DeployRow(dep, d, rate, cost));
        }

        List<LabourCategorySummary> summaries = byCat.entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().ordinal()))
            .map(e -> buildCategorySummary(e.getKey(), e.getValue()))
            .toList();

        return new LabourMasterDashboardSummary(
            projectId,
            deps.size(),
            totalWorkforce,
            dailyPayroll,
            "OMR",
            byCat.size(),
            new LabourMasterDashboardSummary.NationalityMix(omani, expat, omaniOrExpat),
            summaries);
    }

    @Transactional(readOnly = true)
    public List<LabourCategorySummary> byCategory(UUID projectId) {
        return dashboard(projectId).byCategory();
    }

    // ── helpers ─────────────────────────────────────────────────

    private record DeployRow(ProjectLabourDeployment dep, LabourDesignation designation,
                              BigDecimal effectiveRate, BigDecimal dailyCost) {}

    private Map<UUID, LabourDesignation> loadDesignations(List<ProjectLabourDeployment> deps) {
        List<UUID> ids = deps.stream().map(ProjectLabourDeployment::getDesignationId).toList();
        return designationRepo.findAllById(ids).stream()
            .collect(Collectors.toMap(LabourDesignation::getId, d -> d));
    }

    private BigDecimal effectiveRate(ProjectLabourDeployment dep, LabourDesignation d) {
        return dep.getActualDailyRate() != null ? dep.getActualDailyRate() : d.getDefaultDailyRate();
    }

    private LabourCategorySummary buildCategorySummary(LabourCategory cat, List<DeployRow> rows) {
        int designationCount = rows.size();
        int workerCount = rows.stream().mapToInt(r -> r.dep.getWorkerCount()).sum();
        BigDecimal dailyCost = rows.stream().map(DeployRow::dailyCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        String gradeRange = rows.stream().map(r -> r.designation.getGrade().name())
            .distinct().sorted().collect(Collectors.joining(", "));
        BigDecimal minRate = rows.stream().map(DeployRow::effectiveRate)
            .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxRate = rows.stream().map(DeployRow::effectiveRate)
            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        String rateRange = minRate.toPlainString() + " – " + maxRate.toPlainString();
        List<String> roleNames = rows.stream().map(r -> r.designation.getDesignation()).toList();
        String keyRoles = roleNames.size() <= 3
            ? String.join(", ", roleNames)
            : String.join(", ", roleNames.subList(0, 3)) + " +" + (roleNames.size() - 3) + " more";

        return new LabourCategorySummary(
            cat, cat.getDisplayName(), cat.getCodePrefix(),
            designationCount, workerCount, dailyCost,
            gradeRange, rateRange, keyRoles);
    }

    ProjectLabourDeploymentResponse toResponse(ProjectLabourDeployment dep, LabourDesignation d) {
        BigDecimal rate = effectiveRate(dep, d);
        BigDecimal cost = rate.multiply(BigDecimal.valueOf(dep.getWorkerCount()));
        LabourDesignationResponse designationView = designationService.toResponse(d);
        return new ProjectLabourDeploymentResponse(
            dep.getId(), dep.getProjectId(), dep.getDesignationId(),
            dep.getWorkerCount(), dep.getActualDailyRate(), rate, cost, dep.getNotes(),
            designationView);
    }
}
