package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "resources",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_resource_code",
            columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_resource_type", columnList = "resource_type"),
        @Index(name = "idx_resource_status", columnList = "status"),
        @Index(name = "idx_resource_parent_id", columnList = "parent_id"),
        @Index(name = "idx_resource_calendar_id", columnList = "calendar_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 20)
  private ResourceType resourceType;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "calendar_id")
  private UUID calendarId;

  @Column(length = 100)
  private String email;

  @Column(length = 20)
  private String phone;

  @Column(length = 100)
  private String title;

  @Column(name = "max_units_per_day", nullable = false, columnDefinition = "double precision default 8.0")
  @Default
  private Double maxUnitsPerDay = 8.0;

  @Column(name = "default_units_per_time")
  private Double defaultUnitsPerTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'")
  @Default
  private ResourceStatus status = ResourceStatus.ACTIVE;

  @Column(name = "hourly_rate", columnDefinition = "double precision default 0.0")
  @Default
  private Double hourlyRate = 0.0;

  @Column(name = "cost_per_use", columnDefinition = "double precision default 0.0")
  @Default
  private Double costPerUse = 0.0;

  @Column(name = "overtime_rate", columnDefinition = "double precision default 0.0")
  @Default
  private Double overtimeRate = 0.0;

  @Column(name = "sort_order", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
  @Default
  private Integer sortOrder = 0;
}
