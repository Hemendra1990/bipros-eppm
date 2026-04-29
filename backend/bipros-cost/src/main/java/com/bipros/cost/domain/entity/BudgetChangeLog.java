package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budget_change_logs", schema = "cost")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BudgetChangeLog extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "from_wbs_node_id")
    private UUID fromWbsNodeId;

    @Column(name = "to_wbs_node_id")
    private UUID toWbsNodeId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChangeStatus status = ChangeStatus.PENDING;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public enum ChangeType {
        ADDITION,
        REDUCTION,
        TRANSFER
    }

    public enum ChangeStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
