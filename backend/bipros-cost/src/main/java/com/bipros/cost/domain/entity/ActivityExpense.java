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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "activity_expenses", schema = "cost")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityExpense extends BaseEntity {

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "cost_account_id")
    private UUID costAccountId;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "expense_category")
    private String expenseCategory;

    @Column(name = "budgeted_cost", precision = 19, scale = 2)
    private BigDecimal budgetedCost;

    @Column(name = "actual_cost", precision = 19, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "remaining_cost", precision = 19, scale = 2)
    private BigDecimal remainingCost;

    @Column(name = "at_completion_cost", precision = 19, scale = 2)
    private BigDecimal atCompletionCost;

    @Column(name = "percent_complete")
    private Double percentComplete;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_finish_date")
    private LocalDate plannedFinishDate;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_finish_date")
    private LocalDate actualFinishDate;

    @Column(name = "currency")
    private String currency;
}
