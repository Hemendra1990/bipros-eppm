package com.bipros.portfolio.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "portfolio_projects",
    schema = "portfolio",
    uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "project_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioProject extends BaseEntity {

  @Column(nullable = false)
  private UUID portfolioId;

  @Column(nullable = false)
  private UUID projectId;

  @Column
  private Double priorityScore;
}
