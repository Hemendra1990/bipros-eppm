package com.bipros.permit.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.integration.adapter.sms.SmsDispatcher;
import com.bipros.permit.application.dto.GasTestDto;
import com.bipros.permit.application.dto.GasTestRequest;
import com.bipros.permit.application.dto.IsolationPointDto;
import com.bipros.permit.application.dto.IsolationPointRequest;
import com.bipros.permit.application.dto.PermitDetailResponse;
import com.bipros.permit.domain.model.ApprovalStatus;
import com.bipros.permit.domain.model.LifecycleEventType;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitAction;
import com.bipros.permit.domain.model.PermitApproval;
import com.bipros.permit.domain.model.PermitGasTest;
import com.bipros.permit.domain.model.PermitIsolationPoint;
import com.bipros.permit.domain.model.PermitStateMachine;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.repository.PermitApprovalRepository;
import com.bipros.permit.domain.repository.PermitGasTestRepository;
import com.bipros.permit.domain.repository.PermitIsolationPointRepository;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PermitApprovalService {

    private final PermitService permitService;
    private final PermitRepository permitRepository;
    private final PermitApprovalRepository approvalRepository;
    private final PermitGasTestRepository gasTestRepository;
    private final PermitIsolationPointRepository isolationRepository;
    private final PermitTypeTemplateRepository typeTemplateRepository;
    private final PermitMapper mapper;
    private final ProjectAccessGuard projectAccess;
    private final QrCodeService qrCodeService;
    private final SmsDispatcher smsDispatcher;

    public PermitDetailResponse approveStep(UUID permitId, int stepNo, String remarks) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.APPROVE);

        PermitApproval approval = approvalRepository.findByPermitIdAndStepNo(permitId, stepNo)
                .orElseThrow(() -> new ResourceNotFoundException("PermitApproval", stepNo));
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessRuleException("PERMIT_STEP_NOT_PENDING",
                    "Step %d is %s and cannot be approved".formatted(stepNo, approval.getStatus()));
        }
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setReviewerId(projectAccess.currentUserId());
        approval.setReviewedAt(Instant.now());
        approval.setRemarks(remarks);

        permit.setApprovalsCompleted(permit.getApprovalsCompleted() + 1);

        // Advance permit status based on the approved step's role/sequence.
        PermitTypeTemplate type = typeTemplateRepository.findById(permit.getPermitTypeTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("PermitTypeTemplate", permit.getPermitTypeTemplateId()));
        boolean gasTestRequiredAndPending = type.isGasTestRequired() && !hasPassingGasTest(permitId);
        advance(permit, gasTestRequiredAndPending);

        permitService.recordLifecycle(permitId, LifecycleEventType.APPROVED,
                """
                {"step":%d,"role":"%s","remarks":%s}
                """.formatted(stepNo, approval.getRole(),
                        remarks == null ? "null" : "\"" + remarks.replace("\"", "\\\"") + "\""));
        return permitService.getPermit(permitId);
    }

    public PermitDetailResponse rejectStep(UUID permitId, int stepNo, String reason) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.REJECT);

        PermitApproval approval = approvalRepository.findByPermitIdAndStepNo(permitId, stepNo)
                .orElseThrow(() -> new ResourceNotFoundException("PermitApproval", stepNo));
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setReviewerId(projectAccess.currentUserId());
        approval.setReviewedAt(Instant.now());
        approval.setRemarks(reason);

        permit.setStatus(PermitStatus.REJECTED);
        permitService.recordLifecycle(permitId, LifecycleEventType.REJECTED,
                """
                {"step":%d,"reason":"%s"}
                """.formatted(stepNo, reason == null ? "" : reason.replace("\"", "\\\"")));
        return permitService.getPermit(permitId);
    }

    public PermitDetailResponse recordGasTest(UUID permitId, GasTestRequest req) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());

        PermitGasTest g = new PermitGasTest();
        g.setPermitId(permitId);
        g.setLelPct(req.lelPct());
        g.setO2Pct(req.o2Pct());
        g.setH2sPpm(req.h2sPpm());
        g.setCoPpm(req.coPpm());
        g.setResult(req.result());
        g.setTestedBy(projectAccess.currentUserId());
        g.setTestedAt(Instant.now());
        g.setInstrumentSerial(req.instrumentSerial());
        gasTestRepository.save(g);

        if (permit.getStatus() == PermitStatus.AWAITING_GAS_TEST && req.result() != null
                && req.result().name().equals("PASS")) {
            permit.setStatus(PermitStatus.PENDING_HSE);
        }

        permitService.recordLifecycle(permitId, LifecycleEventType.GAS_TEST_RECORDED,
                """
                {"result":"%s","lel":"%s","o2":"%s","h2s":"%s","co":"%s"}
                """.formatted(req.result(), nullSafe(req.lelPct()), nullSafe(req.o2Pct()),
                        nullSafe(req.h2sPpm()), nullSafe(req.coPpm())));
        return permitService.getPermit(permitId);
    }

    public IsolationPointDto applyIsolation(UUID permitId, IsolationPointRequest req) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());

        PermitIsolationPoint p = new PermitIsolationPoint();
        p.setPermitId(permitId);
        p.setIsolationType(req.isolationType());
        p.setPointLabel(req.pointLabel());
        p.setLockNumber(req.lockNumber());
        p.setAppliedAt(Instant.now());
        p.setAppliedBy(projectAccess.currentUserId());
        PermitIsolationPoint saved = isolationRepository.save(p);

        permitService.recordLifecycle(permitId, LifecycleEventType.ISOLATION_APPLIED,
                """
                {"type":"%s","label":"%s"}
                """.formatted(req.isolationType(), escape(req.pointLabel())));
        return mapper.toDto(saved);
    }

    public IsolationPointDto removeIsolation(UUID permitId, UUID pointId) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());

        PermitIsolationPoint p = isolationRepository.findById(pointId)
                .orElseThrow(() -> new ResourceNotFoundException("PermitIsolationPoint", pointId));
        if (!p.getPermitId().equals(permitId)) {
            throw new ResourceNotFoundException("PermitIsolationPoint", pointId);
        }
        p.setRemovedAt(Instant.now());
        p.setRemovedBy(projectAccess.currentUserId());

        permitService.recordLifecycle(permitId, LifecycleEventType.ISOLATION_REMOVED,
                """
                {"label":"%s"}
                """.formatted(escape(p.getPointLabel())));
        return mapper.toDto(p);
    }

    public PermitDetailResponse issue(UUID permitId) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.ISSUE);

        permit.setStatus(PermitStatus.ISSUED);
        permit.setValidFrom(permit.getStartAt());
        permit.setValidTo(permit.getEndAt());
        permit.setQrToken(qrCodeService.generateToken());

        permitService.recordLifecycle(permitId, LifecycleEventType.QR_GENERATED, null);

        SmsDispatcher.SmsDispatchResult result = smsDispatcher.send(new SmsDispatcher.SmsMessage(
                null,
                "Permit %s ISSUED. Valid until %s.".formatted(permit.getPermitCode(), permit.getValidTo()),
                "PERMIT_ISSUED"));
        if (result.success()) {
            permit.setSmsDispatchedAt(Instant.now());
            permitService.recordLifecycle(permitId, LifecycleEventType.SMS_DISPATCHED,
                    "{\"providerMessageId\":\"" + result.providerMessageId() + "\"}");
        }

        return permitService.getPermit(permitId);
    }

    public PermitDetailResponse start(UUID permitId) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.START);
        permit.setStatus(PermitStatus.IN_PROGRESS);
        return permitService.getPermit(permitId);
    }

    private void advance(Permit permit, boolean gasTestRequiredAndPending) {
        // Determine what's left to approve.
        List<PermitApproval> remaining = approvalRepository.findByPermitIdOrderByStepNoAsc(permit.getId()).stream()
                .filter(a -> a.getStatus() == ApprovalStatus.PENDING)
                .toList();

        if (remaining.isEmpty()) {
            permit.setStatus(PermitStatus.APPROVED);
            permit.setCurrentApprovalStep(0);
            return;
        }

        PermitApproval next = remaining.get(0);
        permit.setCurrentApprovalStep(next.getStepNo());

        // Map role → next status; if HSE step is next and gas test is required but missing, divert.
        String role = next.getRole();
        if (role == null) {
            permit.setStatus(PermitStatus.PENDING_HSE);
            return;
        }
        switch (role.toUpperCase()) {
            case "ROLE_SITE_ENGINEER" -> permit.setStatus(PermitStatus.PENDING_SITE_ENGINEER);
            case "ROLE_HSE_OFFICER" -> permit.setStatus(
                    gasTestRequiredAndPending ? PermitStatus.AWAITING_GAS_TEST : PermitStatus.PENDING_HSE);
            case "ROLE_PROJECT_MANAGER" -> permit.setStatus(PermitStatus.PENDING_PM);
            default -> permit.setStatus(PermitStatus.PENDING_HSE);
        }
    }

    private boolean hasPassingGasTest(UUID permitId) {
        return gasTestRepository.findByPermitIdOrderByTestedAtDesc(permitId).stream()
                .anyMatch(g -> g.getResult() != null && g.getResult().name().equals("PASS"));
    }

    public List<GasTestDto> listGasTests(UUID permitId) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireRead(permit.getProjectId());
        return gasTestRepository.findByPermitIdOrderByTestedAtDesc(permitId).stream()
                .map(mapper::toDto).toList();
    }

    public List<IsolationPointDto> listIsolationPoints(UUID permitId) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireRead(permit.getProjectId());
        return isolationRepository.findByPermitId(permitId).stream()
                .map(mapper::toDto).toList();
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
