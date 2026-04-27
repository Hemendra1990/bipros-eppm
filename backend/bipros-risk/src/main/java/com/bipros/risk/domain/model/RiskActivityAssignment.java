package com.bipros.risk.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Links a risk to one or more schedule activities.  When activities are
 * assigned the risk's {@code exposureStartDate} / {@code exposureFinishDate}
 * and exposure cost are recalculated automatically.
 */
@Entity
@Table(
    name = "risk_activity_assignments",
    schema = "risk",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_risk_activity_assignment",
        columnNames = {"risk_id", "activity_id"}),
    indexes = {
        @Index(name = "idx_risk_activity_assignments_risk", columnList = "risk_id"),
        @Index(name = "idx_risk_activity_assignments_activity", columnList = "activity_id"),
        @Index(name = "idx_risk_activity_assignments_project", columnList = "project_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskActivityAssignment extends BaseEntity {

    @Column(name = "risk_id", nullable = false)
    private UUID riskId;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;
}
