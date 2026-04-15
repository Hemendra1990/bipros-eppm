package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.RaBill;
import com.bipros.cost.domain.entity.SatelliteGate;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RaBillDto {
    private UUID id;
    private UUID projectId;
    private UUID contractId;
    private String wbsPackageCode;
    private String billNumber;
    private LocalDate billPeriodFrom;
    private LocalDate billPeriodTo;
    private BigDecimal grossAmount;
    private BigDecimal deductions;
    private BigDecimal mobAdvanceRecovery;
    private BigDecimal retention5Pct;
    private BigDecimal tds2Pct;
    private BigDecimal gst18Pct;
    private BigDecimal netAmount;
    private BigDecimal cumulativeAmount;
    private BigDecimal aiSatellitePercent;
    private BigDecimal contractorClaimedPercent;
    private SatelliteGate satelliteGate;
    private BigDecimal satelliteGateVariance;
    private String pfmsDpaRef;
    private LocalDate paymentDate;
    private String status;
    private LocalDate submittedDate;
    private LocalDate certifiedDate;
    private LocalDate approvedDate;
    private LocalDate paidDate;
    private String certifiedBy;
    private String approvedBy;
    private String remarks;

    public static RaBillDto from(RaBill entity) {
        return RaBillDto.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .contractId(entity.getContractId())
                .wbsPackageCode(entity.getWbsPackageCode())
                .billNumber(entity.getBillNumber())
                .billPeriodFrom(entity.getBillPeriodFrom())
                .billPeriodTo(entity.getBillPeriodTo())
                .grossAmount(entity.getGrossAmount())
                .deductions(entity.getDeductions())
                .mobAdvanceRecovery(entity.getMobAdvanceRecovery())
                .retention5Pct(entity.getRetention5Pct())
                .tds2Pct(entity.getTds2Pct())
                .gst18Pct(entity.getGst18Pct())
                .netAmount(entity.getNetAmount())
                .cumulativeAmount(entity.getCumulativeAmount())
                .aiSatellitePercent(entity.getAiSatellitePercent())
                .contractorClaimedPercent(entity.getContractorClaimedPercent())
                .satelliteGate(entity.getSatelliteGate())
                .satelliteGateVariance(entity.getSatelliteGateVariance())
                .pfmsDpaRef(entity.getPfmsDpaRef())
                .paymentDate(entity.getPaymentDate())
                .status(entity.getStatus().toString())
                .submittedDate(entity.getSubmittedDate())
                .certifiedDate(entity.getCertifiedDate())
                .approvedDate(entity.getApprovedDate())
                .paidDate(entity.getPaidDate())
                .certifiedBy(entity.getCertifiedBy())
                .approvedBy(entity.getApprovedBy())
                .remarks(entity.getRemarks())
                .build();
    }
}
