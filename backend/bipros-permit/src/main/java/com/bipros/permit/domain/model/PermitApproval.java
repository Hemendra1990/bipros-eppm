package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permit_approval", schema = "permit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_permit_step", columnNames = {"permit_id", "step_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitApproval extends BaseEntity {

    @Column(name = "permit_id", nullable = false)
    private UUID permitId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 60)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
