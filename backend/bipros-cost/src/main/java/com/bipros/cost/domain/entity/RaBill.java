package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ra_bills", schema = "cost")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RaBill extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "bill_period_from", nullable = false)
    private LocalDate billPeriodFrom;

    @Column(name = "bill_period_to", nullable = false)
    private LocalDate billPeriodTo;

    @Column(name = "gross_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "deductions", precision = 19, scale = 2)
    private BigDecimal deductions;

    @Column(name = "net_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal netAmount;

    @Column(name = "cumulative_amount", precision = 19, scale = 2)
    private BigDecimal cumulativeAmount;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RaBillStatus status;

    @Column(name = "submitted_date")
    private LocalDate submittedDate;

    @Column(name = "certified_date")
    private LocalDate certifiedDate;

    @Column(name = "approved_date")
    private LocalDate approvedDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "certified_by")
    private String certifiedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "remarks")
    private String remarks;

    public enum RaBillStatus {
        DRAFT, SUBMITTED, CERTIFIED, APPROVED, PAID, REJECTED
    }
}
