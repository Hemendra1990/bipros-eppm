package com.bipros.scheduling.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "pert_estimates", schema = "scheduling", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"activity_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PertEstimate extends BaseEntity {

  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  @Column(name = "optimistic_duration", nullable = false)
  private Double optimisticDuration;

  @Column(name = "most_likely_duration", nullable = false)
  private Double mostLikelyDuration;

  @Column(name = "pessimistic_duration", nullable = false)
  private Double pessimisticDuration;

  @Column(name = "expected_duration", nullable = false)
  private Double expectedDuration;

  @Column(name = "standard_deviation", nullable = false)
  private Double standardDeviation;

  @Column(name = "variance", nullable = false)
  private Double variance;
}
