package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Supervisor Daily Report — Section C (Weather). One row per (project, date) capturing on-site
 * weather & working conditions. Enforced unique on (project_id, log_date) so the service can
 * upsert when a second POST lands for the same date.
 */
@Entity
@Table(
    name = "daily_weather",
    schema = "project",
    indexes = {
        @Index(name = "idx_weather_project_date", columnList = "project_id, log_date")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_weather_project_date", columnNames = {"project_id", "log_date"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyWeather extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "log_date", nullable = false)
  private LocalDate logDate;

  @Column(name = "temp_max_c")
  private Double tempMaxC;

  @Column(name = "temp_min_c")
  private Double tempMinC;

  @Column(name = "rainfall_mm")
  private Double rainfallMm;

  @Column(name = "wind_kmh")
  private Double windKmh;

  @Column(name = "weather_condition", length = 100)
  private String weatherCondition;

  @Column(name = "working_hours")
  private Double workingHours;

  @Column(name = "remarks", length = 500)
  private String remarks;
}
