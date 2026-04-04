package com.bipros.contract.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "contractor_scorecards", schema = "contract")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContractorScorecard extends BaseEntity {

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "period", nullable = false, length = 50)
    private String period;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "safety_score")
    private Double safetyScore;

    @Column(name = "progress_score")
    private Double progressScore;

    @Column(name = "payment_compliance_score")
    private Double paymentComplianceScore;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
