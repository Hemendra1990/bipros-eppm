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
}
