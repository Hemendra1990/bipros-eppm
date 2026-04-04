package com.bipros.baseline.domain;

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
@Table(name = "baselines", schema = "baseline")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Baseline extends BaseEntity {

  @Column(nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private BaselineType baselineType;

  @Column(nullable = false)
  private LocalDate baselineDate;

  @Column(nullable = false)
  private Boolean isActive = true;

  @Column
  private Integer totalActivities;

  @Column
  private BigDecimal totalCost;

  @Column
  private Double projectDuration;

  @Column
  private LocalDate projectStartDate;

  @Column
  private LocalDate projectFinishDate;
}
