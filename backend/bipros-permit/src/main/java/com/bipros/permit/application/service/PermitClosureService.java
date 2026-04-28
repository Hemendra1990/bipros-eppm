package com.bipros.permit.application.service;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.permit.application.dto.PermitDetailResponse;
import com.bipros.permit.domain.model.LifecycleEventType;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitAction;
import com.bipros.permit.domain.model.PermitStateMachine;
import com.bipros.permit.domain.model.PermitStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PermitClosureService {

    private final PermitService permitService;
    private final ProjectAccessGuard projectAccess;

    public PermitDetailResponse close(UUID permitId, String remarks) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.CLOSE);
        permit.setStatus(PermitStatus.CLOSED);
        permit.setClosedAt(Instant.now());
        permit.setClosedBy(projectAccess.currentUserId());
        permit.setCloseRemarks(remarks);
        permit.setQrToken(null);
        permitService.recordLifecycle(permitId, LifecycleEventType.CLOSED, null);
        return permitService.getPermit(permitId);
    }

    public PermitDetailResponse revoke(UUID permitId, String reason) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.REVOKE);
        permit.setStatus(PermitStatus.REVOKED);
        permit.setRevokedAt(Instant.now());
        permit.setRevokedBy(projectAccess.currentUserId());
        permit.setRevokeReason(reason);
        permit.setQrToken(null);
        permitService.recordLifecycle(permitId, LifecycleEventType.REVOKED,
                "{\"reason\":\"" + (reason == null ? "" : reason.replace("\"", "\\\"")) + "\"}");
        return permitService.getPermit(permitId);
    }

    public PermitDetailResponse suspend(UUID permitId, String reason) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.SUSPEND);
        permit.setStatusBeforeSuspend(permit.getStatus());
        permit.setStatus(PermitStatus.SUSPENDED);
        permit.setSuspendedAt(Instant.now());
        permit.setSuspendReason(reason);
        permitService.recordLifecycle(permitId, LifecycleEventType.SUSPENDED,
                "{\"reason\":\"" + (reason == null ? "" : reason.replace("\"", "\\\"")) + "\"}");
        return permitService.getPermit(permitId);
    }

    public PermitDetailResponse resume(UUID permitId) {
        Permit permit = permitService.loadOrThrow(permitId);
        projectAccess.requireEdit(permit.getProjectId());
        PermitStateMachine.assertCanTransition(permit.getStatus(), PermitAction.RESUME);
        PermitStatus restore = permit.getStatusBeforeSuspend() == null
                ? PermitStatus.IN_PROGRESS : permit.getStatusBeforeSuspend();
        permit.setStatus(restore);
        permit.setStatusBeforeSuspend(null);
        permit.setSuspendedAt(null);
        permit.setSuspendReason(null);
        permitService.recordLifecycle(permitId, LifecycleEventType.RESUMED, null);
        return permitService.getPermit(permitId);
    }
}
