package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "funding_sources", schema = "cost")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FundingSource extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "allocated_amount", precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(name = "remaining_amount", precision = 19, scale = 2)
    private BigDecimal remainingAmount;
}
