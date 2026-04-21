package com.bipros.contract.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contracts", schema = "contract", uniqueConstraints = {
    @UniqueConstraint(columnNames = "contract_number"),
    @UniqueConstraint(columnNames = "loa_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Contract extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "tender_id")
    private UUID tenderId;

    @Column(name = "contract_number", nullable = false, length = 50)
    private String contractNumber;

    @Column(name = "loa_number", length = 50)
    private String loaNumber;

    @Column(name = "contractor_name", nullable = false, length = 200)
    private String contractorName;

    @Column(name = "contractor_code", length = 50)
    private String contractorCode;

    @Column(name = "contract_value", precision = 15, scale = 2)
    private BigDecimal contractValue;

    @Column(name = "loa_date")
    private LocalDate loaDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "dlp_months")
    private Integer dlpMonths = 12;

    @Column(name = "ld_rate")
    private Double ldRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false)
    private ContractType contractType;

    // ── IC-PMS M5 denormalised fields (refreshed by ContractKpiService) ──

    /** WBS package code this contract is awarded for (e.g. DMIC-N03-P01). Excel-fidelity string key. */
    @Column(name = "wbs_package_code", length = 60)
    private String wbsPackageCode;

    @Column(name = "package_description", length = 300)
    private String packageDescription;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    /** Denormalised Schedule Performance Index from latest EVM snapshot. */
    @Column(name = "spi", precision = 6, scale = 3)
    private BigDecimal spi;

    /** Denormalised Cost Performance Index from latest EVM snapshot. */
    @Column(name = "cpi", precision = 6, scale = 3)
    private BigDecimal cpi;

    /** AI-derived physical progress percent (0-100) from satellite monitoring. */
    @Column(name = "physical_progress_ai", precision = 5, scale = 2)
    private BigDecimal physicalProgressAi;

    @Column(name = "cumulative_ra_bills_crores", precision = 14, scale = 2)
    private BigDecimal cumulativeRaBillsCrores;

    @Column(name = "vo_numbers_issued")
    private Integer voNumbersIssued;

    @Column(name = "vo_value_crores", precision = 14, scale = 2)
    private BigDecimal voValueCrores;

    /** Contractor performance score 0–100 (quality + safety + progress + payment compliance). */
    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    /** Surfaced from PerformanceBond — earliest expiry for BG-expiry alerts. */
    @Column(name = "bg_expiry")
    private LocalDate bgExpiry;

    /** Last time the denormalised KPI columns were refreshed by ContractKpiService. */
    @Column(name = "kpi_refreshed_at")
    private OffsetDateTime kpiRefreshedAt;
}
