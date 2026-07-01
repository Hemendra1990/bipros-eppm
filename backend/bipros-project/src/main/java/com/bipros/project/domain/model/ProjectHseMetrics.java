package com.bipros.project.domain.model;

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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-project manual HSE inputs backing the HSE statistics tab. One row per project.
 * {@code kmDistanceDriven} is cumulative fleet vehicle-km (a road-safety exposure figure, NOT
 * project chainage). Edited only via the HSE tab — never on the project-creation form.
 */
@Entity
@Table(
    name = "project_hse_metrics",
    schema = "project",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_hse_metrics_project", columnNames = "project_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectHseMetrics extends BaseEntity {

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "km_distance_driven", precision = 19, scale = 3, nullable = false)
    @Builder.Default
    private BigDecimal kmDistanceDriven = BigDecimal.ZERO;
}
