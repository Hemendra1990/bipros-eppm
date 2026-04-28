package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permit", schema = "permit", indexes = {
        @Index(name = "ix_permit_project_status_end", columnList = "project_id, status, end_at"),
        @Index(name = "ix_permit_project_type_created", columnList = "project_id, permit_type_template_id, created_at"),
        @Index(name = "ix_permit_qr_token", columnList = "qr_token", unique = true),
        @Index(name = "ix_permit_code", columnList = "permit_code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Permit extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "permit_code", nullable = false, length = 24)
    private String permitCode;

    @Column(name = "permit_type_template_id", nullable = false)
    private UUID permitTypeTemplateId;

    @Column(name = "parent_permit_id")
    private UUID parentPermitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PermitStatus status = PermitStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel = RiskLevel.MEDIUM;

    @Column(name = "supervisor_name", length = 200)
    private String supervisorName;

    @Column(name = "contractor_org_id")
    private UUID contractorOrgId;

    @Column(name = "location_zone", length = 200)
    private String locationZone;

    @Column(name = "chainage_marker", length = 60)
    private String chainageMarker;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private WorkShift shift = WorkShift.DAY;

    @Column(name = "task_description", columnDefinition = "TEXT")
    private String taskDescription;

    @Column(name = "declaration_accepted_at")
    private Instant declarationAcceptedAt;

    @Column(name = "declaration_accepted_by")
    private UUID declarationAcceptedBy;

    @Column(name = "qr_token", length = 64)
    private String qrToken;

    @Column(name = "sms_dispatched_at")
    private Instant smsDispatchedAt;

    @Column(name = "current_approval_step", nullable = false)
    private int currentApprovalStep;

    @Column(name = "approvals_completed", nullable = false)
    private int approvalsCompleted;

    @Column(name = "total_approvals_required", nullable = false)
    private int totalApprovalsRequired;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "close_remarks", columnDefinition = "TEXT")
    private String closeRemarks;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoke_reason", columnDefinition = "TEXT")
    private String revokeReason;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspend_reason", columnDefinition = "TEXT")
    private String suspendReason;

    @Column(name = "status_before_suspend", length = 30)
    @Enumerated(EnumType.STRING)
    private PermitStatus statusBeforeSuspend;

    @Column(name = "custom_fields_json", columnDefinition = "TEXT")
    private String customFieldsJson;
}
