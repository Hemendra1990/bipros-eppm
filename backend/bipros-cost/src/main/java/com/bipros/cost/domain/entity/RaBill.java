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

    /** Denormalised for satellite-gate lookup (matches Contract.wbsPackageCode). */
    @Column(name = "wbs_package_code", length = 60)
    private String wbsPackageCode;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "bill_period_from", nullable = false)
    private LocalDate billPeriodFrom;

    @Column(name = "bill_period_to", nullable = false)
    private LocalDate billPeriodTo;

    @Column(name = "gross_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal grossAmount;

    /** Legacy lumped deductions — now derived from the 4 breakdown columns below. */
    @Column(name = "deductions", precision = 19, scale = 2)
    private BigDecimal deductions;

    /** Mobilisation advance recovery deduction (M4 spec). */
    @Column(name = "mob_advance_recovery", precision = 19, scale = 2)
    private BigDecimal mobAdvanceRecovery;

    /** 5% retention deduction. */
    @Column(name = "retention_5_pct", precision = 19, scale = 2)
    private BigDecimal retention5Pct;

    /** 2% TDS (Income Tax). */
    @Column(name = "tds_2_pct", precision = 19, scale = 2)
    private BigDecimal tds2Pct;

    /** 18% GST reverse-charge deduction. */
    @Column(name = "gst_18_pct", precision = 19, scale = 2)
    private BigDecimal gst18Pct;

    @Column(name = "net_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal netAmount;

    @Column(name = "cumulative_amount", precision = 19, scale = 2)
    private BigDecimal cumulativeAmount;

    /** AI-derived physical progress % at bill period end (latest satellite scene). */
    @Column(name = "ai_satellite_percent", precision = 5, scale = 2)
    private BigDecimal aiSatellitePercent;

    /** Contractor-claimed progress % for this bill. */
    @Column(name = "contractor_claimed_percent", precision = 5, scale = 2)
    private BigDecimal contractorClaimedPercent;

    /** Pre-approval satellite gate decision. */
    @Enumerated(EnumType.STRING)
    @Column(name = "satellite_gate", length = 40)
    private SatelliteGate satelliteGate;

    /** |contractor - ai| variance used by SatelliteGateService. */
    @Column(name = "satellite_gate_variance", precision = 5, scale = 2)
    private BigDecimal satelliteGateVariance;

    /** PFMS / DPA reference once paid. */
    @Column(name = "pfms_dpa_ref", length = 80)
    private String pfmsDpaRef;

    /** Actual PFMS payment date (distinct from certifiedDate/approvedDate). */
    @Column(name = "payment_date")
    private LocalDate paymentDate;

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
        DRAFT,
        SUBMITTED,
        PMC_REVIEW_PENDING,
        HOLD_SATELLITE_DISPUTE,
        CERTIFIED,
        APPROVED,
        PAID,
        PAID_PMC_OVERRIDE,
        REJECTED
    }
}
