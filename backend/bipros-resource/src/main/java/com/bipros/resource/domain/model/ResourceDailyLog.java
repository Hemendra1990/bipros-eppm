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

import java.time.LocalDate;
import java.util.UUID;

/**
 * IC-PMS M8 daily deployment log per resource. Aggregator rolls these into
 * Resource.utilisationPercent / utilisationStatus and drives the dashboard histogram.
 */
@Entity
@Table(
    name = "resource_daily_logs",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_resource_daily_log",
            columnNames = {"resource_id", "log_date"})
    },
    indexes = {
        @Index(name = "idx_rdl_resource", columnList = "resource_id"),
        @Index(name = "idx_rdl_date", columnList = "log_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceDailyLog extends BaseEntity {

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "planned_units")
    private Double plannedUnits;

    @Column(name = "actual_units")
    private Double actualUnits;

    @Column(name = "utilisation_percent")
    private Double utilisationPercent;

    @Column(name = "wbs_package_code", length = 60)
    private String wbsPackageCode;

    @Column(length = 500)
    private String remarks;
}
