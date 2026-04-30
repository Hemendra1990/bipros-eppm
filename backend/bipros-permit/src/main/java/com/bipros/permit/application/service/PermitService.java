package com.bipros.permit.application.service;

import com.bipros.common.event.PermitLifecycleRecordedEvent;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.permit.application.dto.CreatePermitRequest;
import com.bipros.permit.application.dto.GasTestDto;
import com.bipros.permit.application.dto.IsolationPointDto;
import com.bipros.permit.application.dto.LifecycleEventDto;
import com.bipros.permit.application.dto.PermitApprovalDto;
import com.bipros.permit.application.dto.PermitDetailResponse;
import com.bipros.permit.application.dto.PermitSummary;
import com.bipros.permit.application.dto.PermitWorkerDto;
import com.bipros.permit.application.dto.PpeCheckDto;
import com.bipros.permit.application.dto.UpdatePermitRequest;
import com.bipros.permit.domain.model.ApprovalStatus;
import com.bipros.permit.domain.model.ApprovalStepTemplate;
import com.bipros.permit.domain.model.LifecycleEventType;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitAction;
import com.bipros.permit.domain.model.PermitApproval;
import com.bipros.permit.domain.model.PermitGasTest;
import com.bipros.permit.domain.model.PermitIsolationPoint;
import com.bipros.permit.domain.model.PermitLifecycleEvent;
import com.bipros.permit.domain.model.PermitPpeCheck;
import com.bipros.permit.domain.model.PermitStateMachine;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.model.PermitTypePpe;
import com.bipros.permit.domain.model.PermitWorker;
import com.bipros.permit.domain.model.PpeItemTemplate;
import com.bipros.permit.domain.model.RiskLevel;
import com.bipros.permit.domain.repository.ApprovalStepTemplateRepository;
import com.bipros.permit.domain.repository.PermitApprovalRepository;
import com.bipros.permit.domain.repository.PermitAttachmentRepository;
import com.bipros.permit.domain.repository.PermitGasTestRepository;
import com.bipros.permit.domain.repository.PermitIsolationPointRepository;
import com.bipros.permit.domain.repository.PermitLifecycleEventRepository;
import com.bipros.permit.domain.repository.PermitPpeCheckRepository;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.PermitTypePpeRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import com.bipros.permit.domain.repository.PermitWorkerRepository;
import com.bipros.permit.domain.repository.PpeItemTemplateRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PermitService {

    private final PermitRepository permitRepository;
    private final PermitWorkerRepository workerRepository;
    private final PermitApprovalRepository approvalRepository;
    private final PermitPpeCheckRepository ppeCheckRepository;
    private final PermitGasTestRepository gasTestRepository;
    private final PermitIsolationPointRepository isolationRepository;
    private final PermitLifecycleEventRepository lifecycleRepository;
    private final PermitAttachmentRepository attachmentRepository;
    private final PermitTypeTemplateRepository typeTemplateRepository;
    private final PermitTypePpeRepository typePpeRepository;
    private final PpeItemTemplateRepository ppeItemRepository;
    private final ApprovalStepTemplateRepository approvalStepRepository;
    private final PermitCodeAllocator codeAllocator;
    private final PermitMapper mapper;
    private final ProjectAccessGuard projectAccess;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    // ──────────────────────────────────────────────────────────────────────
    // Create / Update / Submit
    // ──────────────────────────────────────────────────────────────────────

    public PermitDetailResponse createPermit(UUID projectId, CreatePermitRequest req) {
        projectAccess.requireEdit(projectId);

        if (req.startAt() == null || req.endAt() == null || !req.endAt().isAfter(req.startAt())) {
            throw new BusinessRuleException("PERMIT_INVALID_VALIDITY", "endAt must be after startAt");
        }
        PermitTypeTemplate type = typeTemplateRepository.findById(req.permitTypeTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("PermitTypeTemplate", req.permitTypeTemplateId()));

        long durationHours = (req.endAt().toEpochMilli() - req.startAt().toEpochMilli()) / (1000L * 60 * 60);
        if (type.getMaxDurationHours() > 0 && durationHours > type.getMaxDurationHours()) {
            throw new BusinessRuleException("PERMIT_DURATION_EXCEEDED",
                    "Permit type %s allows max %d hours, requested %d".formatted(type.getCode(), type.getMaxDurationHours(), durationHours));
        }

        Permit p = new Permit();
        p.setProjectId(projectId);
        p.setPermitTypeTemplateId(type.getId());
        p.setRiskLevel(req.riskLevel());
        p.setContractorOrgId(req.contractorOrgId());
        p.setSupervisorName(req.supervisorName());
        p.setLocationZone(req.locationZone());
        p.setChainageMarker(req.chainageMarker());
        p.setStartAt(req.startAt());
        p.setEndAt(req.endAt());
        p.setShift(req.shift());
        p.setTaskDescription(req.taskDescription());
        p.setCustomFieldsJson(req.customFieldsJson());
        p.setStatus(PermitStatus.DRAFT);
        p.setCurrentApprovalStep(0);
        p.setApprovalsCompleted(0);
        p.setTotalApprovalsRequired(0);
        p.setPermitCode(codeAllocator.allocate());
        if (req.declarationAccepted()) {
            p.setDeclarationAcceptedAt(Instant.now());
            p.setDeclarationAcceptedBy(projectAccess.currentUserId());
        }
        Permit saved = permitRepository.save(p);

        replaceWorkers(saved.getId(), req.workers());
        rebuildPpeChecks(saved.getId(), type.getId(), req.confirmedPpeItemIds());

        recordLifecycle(saved.getId(), LifecycleEventType.SUBMITTED, null);
        auditService.logCreate("Permit", saved.getId(), summarise(saved));

        return getPermit(saved.getId());
    }

    public PermitDetailResponse updatePermit(UUID projectId, UUID permitId, UpdatePermitRequest req) {
        projectAccess.requireEdit(projectId);
        Permit p = loadInProject(projectId, permitId);
        if (p.getStatus() != PermitStatus.DRAFT) {
            throw new BusinessRuleException("PERMIT_NOT_EDITABLE", "Permit can only be edited while in DRAFT");
        }

        if (req.permitTypeTemplateId() != null) p.setPermitTypeTemplateId(req.permitTypeTemplateId());
        if (req.riskLevel() != null) p.setRiskLevel(req.riskLevel());
        if (req.contractorOrgId() != null) p.setContractorOrgId(req.contractorOrgId());
        if (req.supervisorName() != null) p.setSupervisorName(req.supervisorName());
        if (req.locationZone() != null) p.setLocationZone(req.locationZone());
        if (req.chainageMarker() != null) p.setChainageMarker(req.chainageMarker());
        if (req.startAt() != null) p.setStartAt(req.startAt());
        if (req.endAt() != null) p.setEndAt(req.endAt());
        if (req.shift() != null) p.setShift(req.shift());
        if (req.taskDescription() != null) p.setTaskDescription(req.taskDescription());
        if (req.customFieldsJson() != null) p.setCustomFieldsJson(req.customFieldsJson());

        if (req.workers() != null) replaceWorkers(p.getId(), req.workers());
        if (req.confirmedPpeItemIds() != null) {
            rebuildPpeChecks(p.getId(), p.getPermitTypeTemplateId(), req.confirmedPpeItemIds());
        }
        return getPermit(p.getId());
    }

    public PermitDetailResponse submit(UUID projectId, UUID permitId) {
        projectAccess.requireEdit(projectId);
        Permit p = loadInProject(projectId, permitId);
        PermitStateMachine.assertCanTransition(p.getStatus(), PermitAction.SUBMIT);

        PermitTypeTemplate type = typeTemplateRepository.findById(p.getPermitTypeTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("PermitTypeTemplate", p.getPermitTypeTemplateId()));

        materialiseApprovals(p, type);

        p.setStatus(PermitStatus.PENDING_SITE_ENGINEER);
        p.setCurrentApprovalStep(firstActiveStepNo(p.getId()));
        recordLifecycle(p.getId(), LifecycleEventType.SUBMITTED, null);

        return getPermit(p.getId());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PermitDetailResponse getPermit(UUID permitId) {
        Permit p = permitRepository.findById(permitId)
                .orElseThrow(() -> new ResourceNotFoundException("Permit", permitId));
        projectAccess.requireRead(p.getProjectId());
        return assemble(p);
    }

    @Transactional(readOnly = true)
    public Page<PermitSummary> listPermits(UUID projectId, PermitStatus status, UUID typeId,
                                           RiskLevel riskLevel, Pageable pageable) {
        Set<UUID> accessibleProjects = projectAccess.getAccessibleProjectIdsForCurrentUser();
        Specification<Permit> spec = (root, cq, cb) -> cb.conjunction();
        if (projectId != null) {
            projectAccess.requireRead(projectId);
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("projectId"), projectId));
        } else if (accessibleProjects != null) {
            // null sentinel means ADMIN — no row-level filter; otherwise restrict
            if (accessibleProjects.isEmpty()) {
                return Page.empty(pageable);
            }
            final Set<UUID> ids = accessibleProjects;
            spec = spec.and((root, cq, cb) -> root.get("projectId").in(ids));
        }
        if (status != null) spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), status));
        if (typeId != null) spec = spec.and((root, cq, cb) -> cb.equal(root.get("permitTypeTemplateId"), typeId));
        if (riskLevel != null) spec = spec.and((root, cq, cb) -> cb.equal(root.get("riskLevel"), riskLevel));

        Page<Permit> page = permitRepository.findAll(spec, pageable);
        Map<UUID, PermitTypeTemplate> typeCache = loadTypeCache(page.getContent());
        Map<UUID, PermitWorker> principalByPermit = loadPrincipalWorkers(page.getContent());

        return page.map(p -> toSummary(p, typeCache.get(p.getPermitTypeTemplateId()), principalByPermit.get(p.getId())));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────────────

    /** Used by other services in the module. Not access-gated; callers must gate first. */
    Permit loadOrThrow(UUID permitId) {
        return permitRepository.findById(permitId)
                .orElseThrow(() -> new ResourceNotFoundException("Permit", permitId));
    }

    /** Load + project-access gate. */
    Permit loadInProject(UUID projectId, UUID permitId) {
        Permit p = permitRepository.findById(permitId)
                .orElseThrow(() -> new ResourceNotFoundException("Permit", permitId));
        if (!p.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Permit", permitId);
        }
        return p;
    }

    void recordLifecycle(UUID permitId, LifecycleEventType type, String payloadJson) {
        PermitLifecycleEvent e = new PermitLifecycleEvent();
        e.setPermitId(permitId);
        e.setEventType(type);
        e.setOccurredAt(Instant.now());
        e.setPayloadJson(payloadJson);
        e.setActorUserId(projectAccess.currentUserId());
        lifecycleRepository.save(e);

        Permit permit = permitRepository.findById(permitId).orElse(null);
        if (permit != null) {
            eventPublisher.publishEvent(new PermitLifecycleRecordedEvent(
                    permit.getProjectId(),
                    permit.getId(),
                    permit.getPermitTypeTemplateId(),
                    type.name(),
                    e.getOccurredAt(),
                    e.getActorUserId(),
                    permit.getRiskLevel() != null ? permit.getRiskLevel().name() : null,
                    permit.getStatus() != null ? permit.getStatus().name() : null,
                    payloadJson));
        }
    }

    private void replaceWorkers(UUID permitId, List<PermitWorkerDto> workers) {
        if (workers == null) return;
        workerRepository.deleteByPermitId(permitId);
        entityManager.flush();
        for (PermitWorkerDto w : workers) {
            PermitWorker e = new PermitWorker();
            e.setPermitId(permitId);
            e.setFullName(w.fullName());
            e.setCivilId(w.civilId());
            e.setNationality(w.nationality());
            e.setTrade(w.trade());
            e.setRoleOnPermit(w.roleOnPermit());
            e.setTrainingCertsJson(w.trainingCertsJson());
            workerRepository.save(e);
        }
    }

    private void rebuildPpeChecks(UUID permitId, UUID typeTemplateId, List<UUID> confirmedItemIds) {
        ppeCheckRepository.deleteByPermitId(permitId);
        entityManager.flush();
        Set<UUID> confirmed = new HashSet<>(confirmedItemIds == null ? List.of() : confirmedItemIds);

        List<PermitTypePpe> required = typePpeRepository.findByPermitTypeTemplateId(typeTemplateId);
        for (PermitTypePpe link : required) {
            PermitPpeCheck c = new PermitPpeCheck();
            c.setPermitId(permitId);
            c.setPpeItemTemplateId(link.getPpeItemTemplateId());
            boolean isConfirmed = confirmed.contains(link.getPpeItemTemplateId());
            c.setConfirmed(isConfirmed);
            if (isConfirmed) {
                c.setConfirmedAt(Instant.now());
                c.setConfirmedBy(projectAccess.currentUserId());
            }
            ppeCheckRepository.save(c);
        }
    }

    /**
     * At submit time, materialise one row in {@code permit_approval} per template step.
     * Steps not required for the permit's risk level are inserted with {@code SKIPPED}
     * so the timeline UI always shows the full chain.
     */
    private void materialiseApprovals(Permit permit, PermitTypeTemplate type) {
        List<ApprovalStepTemplate> templates = approvalStepRepository.findByPermitTypeTemplateIdOrderByStepNoAsc(type.getId());
        int total = 0;
        for (ApprovalStepTemplate t : templates) {
            PermitApproval a = new PermitApproval();
            a.setPermitId(permit.getId());
            a.setStepNo(t.getStepNo());
            a.setLabel(t.getLabel());
            a.setRole(t.getRole());
            boolean required = stepRequiredForRisk(t.getRequiredForRiskLevels(), permit.getRiskLevel());
            a.setStatus(required ? ApprovalStatus.PENDING : ApprovalStatus.SKIPPED);
            approvalRepository.save(a);
            if (required) total++;
        }
        permit.setTotalApprovalsRequired(total);
    }

    private boolean stepRequiredForRisk(String csv, RiskLevel riskLevel) {
        if (csv == null || csv.isBlank()) return true;
        Set<String> required = Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return required.contains(riskLevel.name());
    }

    private int firstActiveStepNo(UUID permitId) {
        return approvalRepository.findByPermitIdOrderByStepNoAsc(permitId).stream()
                .filter(a -> a.getStatus() == ApprovalStatus.PENDING)
                .map(PermitApproval::getStepNo)
                .findFirst()
                .orElse(0);
    }

    private PermitDetailResponse assemble(Permit p) {
        PermitTypeTemplate type = typeTemplateRepository.findById(p.getPermitTypeTemplateId()).orElse(null);

        List<PermitWorkerDto> workers = workerRepository.findByPermitId(p.getId()).stream()
                .map(mapper::toDto).toList();

        List<PermitApprovalDto> approvals = approvalRepository.findByPermitIdOrderByStepNoAsc(p.getId()).stream()
                .map(mapper::toDto).toList();

        List<PermitPpeCheck> rawChecks = ppeCheckRepository.findByPermitId(p.getId());
        Map<UUID, PpeItemTemplate> ppeItems = ppeItemRepository.findAllById(
                        rawChecks.stream().map(PermitPpeCheck::getPpeItemTemplateId).toList())
                .stream().collect(Collectors.toMap(PpeItemTemplate::getId, x -> x));
        List<PpeCheckDto> ppeChecks = rawChecks.stream()
                .map(c -> mapper.toDto(c, ppeItems.get(c.getPpeItemTemplateId())))
                .sorted(Comparator.comparing(PpeCheckDto::ppeItemCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<GasTestDto> gasTests = gasTestRepository.findByPermitIdOrderByTestedAtDesc(p.getId()).stream()
                .map(mapper::toDto).toList();

        List<IsolationPointDto> isolations = isolationRepository.findByPermitId(p.getId()).stream()
                .map(mapper::toDto).toList();

        List<LifecycleEventDto> events = lifecycleRepository.findByPermitIdOrderByOccurredAtDesc(p.getId()).stream()
                .map(mapper::toDto).toList();

        return new PermitDetailResponse(
                p.getId(),
                p.getPermitCode(),
                p.getProjectId(),
                p.getParentPermitId(),
                p.getPermitTypeTemplateId(),
                type != null ? type.getCode() : null,
                type != null ? type.getName() : null,
                type != null ? type.getColorHex() : null,
                type != null ? type.getIconKey() : null,
                p.getStatus(),
                p.getRiskLevel(),
                p.getContractorOrgId(),
                p.getSupervisorName(),
                p.getLocationZone(),
                p.getChainageMarker(),
                p.getStartAt(),
                p.getEndAt(),
                p.getValidFrom(),
                p.getValidTo(),
                p.getShift(),
                p.getTaskDescription(),
                p.getDeclarationAcceptedAt(),
                p.getDeclarationAcceptedBy(),
                p.getQrToken() != null,
                p.getSmsDispatchedAt(),
                p.getCurrentApprovalStep(),
                p.getApprovalsCompleted(),
                p.getTotalApprovalsRequired(),
                p.getClosedAt(),
                p.getClosedBy(),
                p.getCloseRemarks(),
                p.getRevokedAt(),
                p.getRevokedBy(),
                p.getRevokeReason(),
                p.getExpiredAt(),
                p.getSuspendedAt(),
                p.getSuspendReason(),
                p.getCustomFieldsJson(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                null,
                workers,
                approvals,
                ppeChecks,
                gasTests,
                isolations,
                events
        );
    }

    private PermitSummary toSummary(Permit p, PermitTypeTemplate type, PermitWorker principal) {
        return new PermitSummary(
                p.getId(),
                p.getPermitCode(),
                p.getProjectId(),
                p.getPermitTypeTemplateId(),
                type != null ? type.getCode() : null,
                type != null ? type.getName() : null,
                type != null ? type.getColorHex() : null,
                type != null ? type.getIconKey() : null,
                p.getStatus(),
                p.getRiskLevel(),
                p.getShift(),
                p.getTaskDescription(),
                principal != null ? principal.getFullName() : null,
                principal != null ? principal.getNationality() : null,
                p.getStartAt(),
                p.getEndAt()
        );
    }

    private Map<UUID, PermitTypeTemplate> loadTypeCache(List<Permit> permits) {
        Set<UUID> typeIds = permits.stream().map(Permit::getPermitTypeTemplateId).collect(Collectors.toSet());
        return typeTemplateRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(PermitTypeTemplate::getId, x -> x));
    }

    private Map<UUID, PermitWorker> loadPrincipalWorkers(List<Permit> permits) {
        if (permits.isEmpty()) return Map.of();
        Map<UUID, PermitWorker> result = new HashMap<>();
        for (Permit p : permits) {
            workerRepository.findByPermitId(p.getId()).stream()
                    .filter(w -> w.getRoleOnPermit() != null && w.getRoleOnPermit().name().equals("PRINCIPAL"))
                    .findFirst()
                    .ifPresentOrElse(
                            w -> result.put(p.getId(), w),
                            () -> workerRepository.findByPermitId(p.getId()).stream().findFirst()
                                    .ifPresent(w -> result.put(p.getId(), w))
                    );
        }
        return result;
    }

    private Map<String, Object> summarise(Permit p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("permitCode", p.getPermitCode());
        m.put("projectId", p.getProjectId());
        m.put("status", p.getStatus());
        m.put("riskLevel", p.getRiskLevel());
        m.put("permitTypeTemplateId", p.getPermitTypeTemplateId());
        return m;
    }

    /** Used by the (admin only) delete path. */
    public void deletePermit(UUID projectId, UUID permitId) {
        projectAccess.requireDelete(projectId);
        Permit p = loadInProject(projectId, permitId);
        if (PermitStateMachine.isTerminal(p.getStatus()) || p.getStatus() == PermitStatus.DRAFT) {
            workerRepository.deleteByPermitId(p.getId());
            ppeCheckRepository.deleteByPermitId(p.getId());
            approvalRepository.deleteAll(approvalRepository.findByPermitIdOrderByStepNoAsc(p.getId()));
            attachmentRepository.deleteAll(attachmentRepository.findByPermitId(p.getId()));
            isolationRepository.deleteAll(isolationRepository.findByPermitId(p.getId()));
            gasTestRepository.deleteAll(gasTestRepository.findByPermitIdOrderByTestedAtDesc(p.getId()));
            lifecycleRepository.deleteAll(lifecycleRepository.findByPermitIdOrderByOccurredAtDesc(p.getId()));
            permitRepository.delete(p);
            auditService.logDelete("Permit", p.getId());
        } else {
            throw new BusinessRuleException("PERMIT_NOT_DELETABLE",
                    "Permit must be DRAFT or terminal to be deleted; current status: " + p.getStatus());
        }
    }

    /** Convenience used by other services. */
    public List<PermitApproval> listApprovals(UUID permitId) {
        return new ArrayList<>(approvalRepository.findByPermitIdOrderByStepNoAsc(permitId));
    }
}
