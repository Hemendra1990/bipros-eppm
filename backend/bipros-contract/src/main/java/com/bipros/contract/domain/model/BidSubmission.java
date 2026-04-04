package com.bipros.contract.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bid_submissions", schema = "contract")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BidSubmission extends BaseEntity {

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Column(name = "bidder_name", nullable = false, length = 200)
    private String bidderName;

    @Column(name = "bidder_code", length = 50)
    private String bidderCode;

    @Column(name = "technical_score")
    private Double technicalScore;

    @Column(name = "financial_bid", precision = 15, scale = 2)
    private BigDecimal financialBid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidSubmissionStatus status = BidSubmissionStatus.SUBMITTED;

    @Column(name = "evaluation_remarks", columnDefinition = "TEXT")
    private String evaluationRemarks;
}
