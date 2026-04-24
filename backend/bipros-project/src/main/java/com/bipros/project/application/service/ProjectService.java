package com.bipros.project.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.project.application.dto.CreateProjectRequest;
import com.bipros.project.application.dto.CreateProjectRequest.ContractSummaryInput;
import com.bipros.project.application.dto.ProjectResponse;
import com.bipros.project.application.dto.ProjectResponse.ContractSummary;
import com.bipros.project.application.dto.UpdateProjectRequest;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectActivityCounter;
import com.bipros.project.domain.repository.EpsNodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EpsNodeRepository epsNodeRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final ProjectActivityCounter projectActivityCounter;
    private final AuditService auditService;
    private final ContractRepository contractRepository;

    public ProjectResponse createProject(CreateProjectRequest request) {
        log.info("Creating project with code: {}", request.code());

        if (projectRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("PROJECT_CODE_DUPLICATE", "Project with code '" + request.code() + "' already exists");
        }

        if (!epsNodeRepository.existsById(request.epsNodeId())) {
            throw new ResourceNotFoundException("EpsNode", request.epsNodeId());
        }

        if (request.plannedStartDate() != null
            && request.plannedFinishDate() != null
            && request.plannedFinishDate().isBefore(request.plannedStartDate())) {
            throw new BusinessRuleException(
                "INVALID_DATE_RANGE",
                "plannedFinishDate must be on or after plannedStartDate");
        }

        validateChainage(request.fromChainageM(), request.toChainageM());

        Project project = new Project();
        project.setCode(request.code());
        project.setName(sanitizeText(request.name()));
        project.setDescription(sanitizeText(request.description()));
        project.setEpsNodeId(request.epsNodeId());
        project.setObsNodeId(request.obsNodeId());
        project.setPlannedStartDate(request.plannedStartDate());
        project.setPlannedFinishDate(request.plannedFinishDate());
        if (request.priority() != null) {
            project.setPriority(request.priority());
        }
        project.setCategory(request.category());
        project.setMorthCode(request.morthCode());
        project.setFromChainageM(request.fromChainageM());
        project.setToChainageM(request.toChainageM());
        project.setFromLocation(sanitizeText(request.fromLocation()));
        project.setToLocation(sanitizeText(request.toLocation()));
        project.setTotalLengthKm(deriveTotalLengthKm(
            request.fromChainageM(), request.toChainageM(), request.totalLengthKm()));

        Project saved = projectRepository.save(project);
        log.info("Project created with ID: {}", saved.getId());

        if (request.contract() != null) {
            upsertPrimaryContract(saved, request.contract());
        }

        // Audit log creation
        auditService.logCreate("Project", saved.getId(), buildProjectResponse(saved));

        // Auto-create root WBS node
        createRootWbsNode(saved);

        return buildProjectResponse(saved);
    }

    public ProjectResponse updateProject(UUID id, UpdateProjectRequest request) {
        log.info("Updating project: {}", id);

        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        // Track changes for audit
        String oldName = project.getName();
        String oldDescription = project.getDescription();
        UUID oldObsNodeId = project.getObsNodeId();
        var oldPlannedStart = project.getPlannedStartDate();
        var oldPlannedFinish = project.getPlannedFinishDate();
        var oldMustFinishBy = project.getMustFinishByDate();
        var oldStatus = project.getStatus();
        var oldPriority = project.getPriority();
        var oldDataDate = project.getDataDate();

        if (request.name() != null) {
            project.setName(sanitizeText(request.name()));
        }
        if (request.description() != null) {
            project.setDescription(sanitizeText(request.description()));
        }
        if (request.obsNodeId() != null) {
            project.setObsNodeId(request.obsNodeId());
        }
        if (request.plannedStartDate() != null) {
            project.setPlannedStartDate(request.plannedStartDate());
        }
        if (request.plannedFinishDate() != null) {
            project.setPlannedFinishDate(request.plannedFinishDate());
        }

        var resolvedStart = project.getPlannedStartDate();
        var resolvedFinish = project.getPlannedFinishDate();
        if (resolvedStart != null && resolvedFinish != null && resolvedFinish.isBefore(resolvedStart)) {
            throw new BusinessRuleException(
                "INVALID_DATE_RANGE",
                "plannedFinishDate must be on or after plannedStartDate");
        }
        if (request.mustFinishByDate() != null) {
            project.setMustFinishByDate(request.mustFinishByDate());
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        if (request.priority() != null) {
            project.setPriority(request.priority());
        }
        if (request.dataDate() != null) {
            project.setDataDate(request.dataDate());
        }
        if (request.category() != null) {
            project.setCategory(request.category());
        }
        if (request.morthCode() != null) {
            project.setMorthCode(request.morthCode());
        }
        if (request.fromChainageM() != null) {
            project.setFromChainageM(request.fromChainageM());
        }
        if (request.toChainageM() != null) {
            project.setToChainageM(request.toChainageM());
        }
        if (request.fromLocation() != null) {
            project.setFromLocation(sanitizeText(request.fromLocation()));
        }
        if (request.toLocation() != null) {
            project.setToLocation(sanitizeText(request.toLocation()));
        }
        validateChainage(project.getFromChainageM(), project.getToChainageM());
        // Recompute derived length whenever chainages change (respecting an explicit override).
        if (request.fromChainageM() != null || request.toChainageM() != null
            || request.totalLengthKm() != null) {
            project.setTotalLengthKm(deriveTotalLengthKm(
                project.getFromChainageM(), project.getToChainageM(), request.totalLengthKm()));
        }

        Project updated = projectRepository.save(project);
        log.info("Project updated: {}", id);

        if (request.contract() != null) {
            upsertPrimaryContract(updated, request.contract());
        }

        // Audit log updates for all changed fields
        if (request.name() != null && !request.name().equals(oldName)) {
            auditService.logUpdate("Project", id, "name", oldName, request.name());
        }
        if (request.description() != null && !request.description().equals(oldDescription)) {
            auditService.logUpdate("Project", id, "description", oldDescription, request.description());
        }
        if (request.obsNodeId() != null && !request.obsNodeId().equals(oldObsNodeId)) {
            auditService.logUpdate("Project", id, "obsNodeId", oldObsNodeId, request.obsNodeId());
        }
        if (request.plannedStartDate() != null && !request.plannedStartDate().equals(oldPlannedStart)) {
            auditService.logUpdate("Project", id, "plannedStartDate", oldPlannedStart, request.plannedStartDate());
        }
        if (request.plannedFinishDate() != null && !request.plannedFinishDate().equals(oldPlannedFinish)) {
            auditService.logUpdate("Project", id, "plannedFinishDate", oldPlannedFinish, request.plannedFinishDate());
        }
        if (request.mustFinishByDate() != null && !request.mustFinishByDate().equals(oldMustFinishBy)) {
            auditService.logUpdate("Project", id, "mustFinishByDate", oldMustFinishBy, request.mustFinishByDate());
        }
        if (request.status() != null && !request.status().equals(oldStatus)) {
            auditService.logUpdate("Project", id, "status", oldStatus, request.status());
        }
        if (request.priority() != null && !request.priority().equals(oldPriority)) {
            auditService.logUpdate("Project", id, "priority", oldPriority, request.priority());
        }
        if (request.dataDate() != null && !request.dataDate().equals(oldDataDate)) {
            auditService.logUpdate("Project", id, "dataDate", oldDataDate, request.dataDate());
        }

        return buildProjectResponse(updated);
    }

    public void deleteProject(UUID id) {
        log.info("Deleting project: {}", id);

        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        // Check if activities exist for this project
        long activityCount = projectActivityCounter.countActivitiesByProjectId(id);
        if (activityCount > 0) {
            throw new BusinessRuleException("PROJECT_HAS_ACTIVITIES", "Cannot delete project with existing activities");
        }

        // Delete associated WBS nodes
        List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(id);
        wbsNodeRepository.deleteAll(wbsNodes);

        projectRepository.delete(project);
        log.info("Project deleted: {}", id);

        // Audit log deletion
        auditService.logDelete("Project", id);
    }

    public ProjectResponse getProject(UUID id) {
        log.info("Fetching project: {}", id);

        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        return buildProjectResponse(project);
    }

    public PagedResponse<ProjectResponse> listProjects(Pageable pageable) {
        log.info("Fetching projects page: {}", pageable);

        Page<Project> page = projectRepository.findAll(pageable);

        List<ProjectResponse> content = page.getContent().stream()
            .map(this::buildProjectResponse)
            .collect(Collectors.toList());

        return PagedResponse.of(
            content,
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    public List<ProjectResponse> getProjectsByEps(UUID epsNodeId) {
        log.info("Fetching projects by EPS node: {}", epsNodeId);

        if (!epsNodeRepository.existsById(epsNodeId)) {
            throw new ResourceNotFoundException("EpsNode", epsNodeId);
        }

        return projectRepository.findByEpsNodeId(epsNodeId).stream()
            .map(this::buildProjectResponse)
            .collect(Collectors.toList());
    }

    private void createRootWbsNode(Project project) {
        WbsNode rootWbs = new WbsNode();
        rootWbs.setCode(project.getCode());
        rootWbs.setName(project.getName());
        rootWbs.setProjectId(project.getId());
        rootWbs.setParentId(null);
        rootWbs.setSortOrder(0);

        wbsNodeRepository.save(rootWbs);
        log.info("Root WBS node created for project: {}", project.getId());
    }

    private ProjectResponse buildProjectResponse(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getCode(),
            project.getName(),
            project.getDescription(),
            project.getEpsNodeId(),
            project.getObsNodeId(),
            project.getPlannedStartDate(),
            project.getPlannedFinishDate(),
            project.getDataDate(),
            project.getStatus(),
            project.getMustFinishByDate(),
            project.getPriority(),
            project.getCategory(),
            project.getMorthCode(),
            project.getFromChainageM(),
            project.getToChainageM(),
            project.getFromLocation(),
            project.getToLocation(),
            project.getTotalLengthKm(),
            primaryContractSummary(project.getId()),
            toLocalDateTime(project.getCreatedAt()),
            toLocalDateTime(project.getUpdatedAt())
        );
    }

    /**
     * Pick the "primary" contract for a project. Prefers the first ACTIVE row; otherwise falls
     * back to the earliest-started row. Returns {@code null} if the project has no contracts yet.
     */
    private ContractSummary primaryContractSummary(UUID projectId) {
        List<Contract> contracts = contractRepository.findByProjectId(projectId);
        if (contracts.isEmpty()) return null;
        Contract primary = contracts.stream()
            .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
            .findFirst()
            .orElseGet(() -> contracts.stream()
                .sorted(Comparator.comparing(Contract::getStartDate,
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst().orElse(null));
        if (primary == null) return null;
        return new ContractSummary(
            primary.getId(),
            primary.getContractNumber(),
            primary.getContractType(),
            primary.getContractValue(),
            primary.getRevisedValue(),
            primary.getStartDate(),
            primary.getCompletionDate(),
            primary.getDlpMonths()
        );
    }

    /**
     * Upsert the project's primary Contract from the flat {@link ContractSummaryInput}. Creates
     * a new row the first time; otherwise updates the existing primary in place. Validates that
     * {@code revisedValue >= contractValue} when both are present.
     */
    private void upsertPrimaryContract(Project project, ContractSummaryInput input) {
        if (input == null) return;
        if (input.revisedValue() != null && input.contractValue() != null
            && input.revisedValue().compareTo(input.contractValue()) < 0) {
            throw new BusinessRuleException(
                "INVALID_CONTRACT_VALUE",
                "revisedValue must be greater than or equal to contractValue");
        }
        List<Contract> existing = contractRepository.findByProjectId(project.getId());
        Contract contract = existing.stream()
            .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
            .findFirst()
            .orElseGet(() -> existing.isEmpty() ? new Contract() : existing.get(0));
        boolean isNew = contract.getId() == null;
        contract.setProjectId(project.getId());
        if (input.contractNumber() != null) contract.setContractNumber(input.contractNumber());
        if (contract.getContractNumber() == null || contract.getContractNumber().isBlank()) {
            // Contract number is mandatory on the schema; synthesise one if the caller omitted it.
            contract.setContractNumber("CT-" + project.getCode());
        }
        if (input.contractType() != null) contract.setContractType(input.contractType());
        if (input.contractValue() != null) contract.setContractValue(input.contractValue());
        if (input.revisedValue() != null) contract.setRevisedValue(input.revisedValue());
        if (input.startDate() != null) contract.setStartDate(input.startDate());
        if (input.completionDate() != null) contract.setCompletionDate(input.completionDate());
        if (input.dlpMonths() != null) contract.setDlpMonths(input.dlpMonths());
        if (input.contractorName() != null) contract.setContractorName(input.contractorName());
        if (contract.getContractorName() == null || contract.getContractorName().isBlank()) {
            contract.setContractorName("TBD");
        }
        if (contract.getContractType() == null) {
            // Contract.contractType is NOT NULL — fall back to LUMP_SUM when the caller hasn't chosen.
            contract.setContractType(com.bipros.contract.domain.model.ContractType.LUMP_SUM);
        }
        contractRepository.save(contract);
        if (isNew) {
            log.info("Primary contract created for project: projectId={}, contractNumber={}",
                project.getId(), contract.getContractNumber());
        }
    }

    private void validateChainage(Long from, Long to) {
        if (from != null && to != null && to < from) {
            throw new BusinessRuleException(
                "INVALID_CHAINAGE_RANGE",
                "toChainageM must be on or after fromChainageM");
        }
    }

    private BigDecimal deriveTotalLengthKm(Long from, Long to, BigDecimal explicit) {
        if (explicit != null) return explicit;
        if (from == null || to == null || to < from) return null;
        return BigDecimal.valueOf(to - from)
            .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }

    /**
     * Strip HTML-ish tags from free-text fields. The UI escapes on render so there's no XSS risk,
     * but storing {@code <script>} payloads verbatim fails data-hygiene reviews and pollutes
     * exports/search. Tags are removed (not encoded) so the value round-trips cleanly through
     * downstream consumers.
     */
    private static String sanitizeText(String value) {
        if (value == null) return null;
        String stripped = value.replaceAll("<[^>]*>", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
