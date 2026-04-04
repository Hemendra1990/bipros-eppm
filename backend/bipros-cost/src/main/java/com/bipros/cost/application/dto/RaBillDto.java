package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.RaBill;
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
    private String billNumber;
    private LocalDate billPeriodFrom;
    private LocalDate billPeriodTo;
    private BigDecimal grossAmount;
    private BigDecimal deductions;
    private BigDecimal netAmount;
    private BigDecimal cumulativeAmount;
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
                .billNumber(entity.getBillNumber())
                .billPeriodFrom(entity.getBillPeriodFrom())
                .billPeriodTo(entity.getBillPeriodTo())
                .grossAmount(entity.getGrossAmount())
                .deductions(entity.getDeductions())
                .netAmount(entity.getNetAmount())
                .cumulativeAmount(entity.getCumulativeAmount())
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
