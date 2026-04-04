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
@Table(name = "retention_money", schema = "cost")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetentionMoney extends BaseEntity {

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "retention_percentage")
    private Double retentionPercentage;

    @Column(name = "retained_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal retainedAmount;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "released_amount", precision = 19, scale = 2)
    private BigDecimal releasedAmount;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RetentionStatus status;

    public enum RetentionStatus {
        RETAINED, PARTIALLY_RELEASED, FULLY_RELEASED
    }
}
