package com.bipros.contract.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tenders", schema = "contract", uniqueConstraints = {
    @UniqueConstraint(columnNames = "tender_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Tender extends BaseEntity {

    @Column(name = "procurement_plan_id", nullable = false)
    private UUID procurementPlanId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "tender_number", nullable = false, length = 50)
    private String tenderNumber;

    @Column(name = "nit_date")
    private LocalDate nitDate;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Column(name = "estimated_value", precision = 15, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "emd_amount", precision = 15, scale = 2)
    private BigDecimal emdAmount;

    @Column(name = "completion_period_days")
    private Integer completionPeriodDays;

    @Column(name = "bid_due_date")
    private LocalDate bidDueDate;

    @Column(name = "bid_open_date")
    private LocalDate bidOpenDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenderStatus status = TenderStatus.DRAFT;

    @Column(name = "awarded_contract_id")
    private UUID awardedContractId;
}
