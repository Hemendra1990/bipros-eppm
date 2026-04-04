package com.bipros.activity.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "activity_steps", schema = "activity")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityStep extends BaseEntity {

  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private Double weight = 1.0;

  @Column(name = "weight_percent")
  private Double weightPercent;

  @Column(name = "is_completed", nullable = false)
  private Boolean isCompleted = false;

  @Column(name = "sort_order")
  private Integer sortOrder;
}
