package com.bipros.contract.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "procurement_plans", schema = "contract", uniqueConstraints = {
    @UniqueConstraint(columnNames = "plan_code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcurementPlan extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "wbs_node_id")
    private UUID wbsNodeId;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "procurement_method", nullable = false)
    private ProcurementMethod procurementMethod;

    @Column(name = "estimated_value", precision = 15, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "currency", length = 3)
    private String currency = "INR";

    @Column(name = "target_nit_date")
    private LocalDate targetNitDate;

    @Column(name = "target_award_date")
    private LocalDate targetAwardDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcurementPlanStatus status = ProcurementPlanStatus.DRAFT;

    @Column(name = "approval_level", length = 50)
    private String approvalLevel;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
