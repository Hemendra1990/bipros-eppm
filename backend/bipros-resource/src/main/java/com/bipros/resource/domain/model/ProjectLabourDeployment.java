package com.bipros.resource.domain.model;

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "project_labour_deployments",
    schema = "resource",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_project_labour_deployment_project_designation",
        columnNames = {"project_id", "designation_id"}),
    indexes = {
        @Index(name = "idx_project_labour_deployment_project", columnList = "project_id"),
        @Index(name = "idx_project_labour_deployment_designation", columnList = "designation_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectLabourDeployment extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "designation_id", nullable = false)
    private UUID designationId;

    @Column(name = "worker_count", nullable = false)
    private Integer workerCount;

    @Column(name = "actual_daily_rate", precision = 10, scale = 2)
    private BigDecimal actualDailyRate;

    @Column(length = 500)
    private String notes;
}
