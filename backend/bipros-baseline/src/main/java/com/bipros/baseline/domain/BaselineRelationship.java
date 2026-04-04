package com.bipros.baseline.domain;

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
@Table(name = "baseline_relationships", schema = "baseline")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaselineRelationship extends BaseEntity {

  @Column(nullable = false)
  private UUID baselineId;

  @Column
  private UUID predecessorActivityId;

  @Column
  private UUID successorActivityId;

  @Column
  private String relationshipType;

  @Column
  private Double lag;
}
