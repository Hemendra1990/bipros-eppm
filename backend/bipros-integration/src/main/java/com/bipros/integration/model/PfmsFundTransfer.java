package com.bipros.integration.model;

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
@Table(name = "fund_transfers", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PfmsFundTransfer extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "pfms_reference_number", length = 100)
    private String pfmsReferenceNumber;

    @Column(name = "sanction_order_number", nullable = false, length = 100)
    private String sanctionOrderNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "beneficiary", nullable = false, length = 255)
    private String beneficiary;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private FundTransferStatus status = FundTransferStatus.INITIATED;

    @Column(name = "pfms_status", length = 100)
    private String pfmsStatus;

    public enum FundTransferStatus {
        INITIATED,
        PROCESSING,
        COMPLETED,
        FAILED,
        REVERSED
    }
}
