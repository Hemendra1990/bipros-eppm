package com.bipros.portfolio.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scoring_models", schema = "portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoringModel extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @Column(nullable = false)
  private Boolean isDefault = false;
}
