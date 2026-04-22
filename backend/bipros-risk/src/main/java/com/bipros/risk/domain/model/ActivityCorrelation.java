package com.bipros.risk.domain.model;

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

/**
 * Pertmaster-style "Duration Correlation": pairwise rank correlation coefficient between
 * two activities' sampled durations. Enforced in the simulation via Iman-Conover reshuffling.
 * Stored unordered (a ↔ b symmetric); the service canonicalises (smaller-id, larger-id) on write.
 */
@Entity
@Table(
    name = "activity_correlations",
    schema = "risk",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "activity_a_id", "activity_b_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityCorrelation extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "activity_a_id", nullable = false)
    private UUID activityAId;

    @Column(name = "activity_b_id", nullable = false)
    private UUID activityBId;

    /** Pearson/rank correlation in the interval (-1, 1). */
    @Column(nullable = false)
    private Double coefficient;
}
