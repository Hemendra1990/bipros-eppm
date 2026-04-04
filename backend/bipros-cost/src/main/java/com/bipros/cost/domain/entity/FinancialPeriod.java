package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "financial_periods", schema = "cost")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FinancialPeriod extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "period_type")
    private String periodType;

    @Column(name = "is_closed")
    private Boolean isClosed;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
