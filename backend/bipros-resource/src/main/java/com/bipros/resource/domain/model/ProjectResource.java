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
    name = "project_resources",
    schema = "resource",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_project_resource", columnNames = {"project_id", "resource_id"}),
    indexes = {
        @Index(name = "idx_pr_project_id", columnList = "project_id"),
        @Index(name = "idx_pr_resource_id", columnList = "resource_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResource extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "rate_override", precision = 19, scale = 4)
    private BigDecimal rateOverride;

    @Column(name = "availability_override")
    private Double availabilityOverride;

    @Column(name = "custom_unit", length = 50)
    private String customUnit;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
