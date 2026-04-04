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
    name = "project_scores",
    schema = "portfolio",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "scoring_criterion_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectScore extends BaseEntity {

  @Column(nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private UUID scoringModelId;

  @Column(nullable = false)
  private UUID scoringCriterionId;

  @Column(nullable = false)
  private Double score;
}
