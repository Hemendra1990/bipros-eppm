package com.bipros.portfolio.domain;

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
@Table(name = "portfolio_scenario_projects", schema = "portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioScenarioProject extends BaseEntity {

  @Column(nullable = false)
  private UUID scenarioId;

  @Column(nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private Boolean included = true;

  @Column
  private LocalDate adjustedStartDate;

  @Column
  private Integer adjustedPriority;

  @Column
  private BigDecimal adjustedBudget;
}
