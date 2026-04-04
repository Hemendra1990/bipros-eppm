package com.bipros.portfolio.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "scoring_criteria", schema = "portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoringCriterion extends BaseEntity {

  @Column(nullable = false)
  private UUID scoringModelId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private Double weight;

  @Column(nullable = false)
  private Double minScore = 0.0;

  @Column(nullable = false)
  private Double maxScore = 10.0;

  @Column(nullable = false)
  private Integer sortOrder;
}
