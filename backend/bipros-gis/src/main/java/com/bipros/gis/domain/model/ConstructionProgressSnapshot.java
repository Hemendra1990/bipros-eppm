package com.bipros.gis.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "construction_progress_snapshots", schema = "gis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConstructionProgressSnapshot extends BaseEntity {

    @NotNull(message = "Project ID is required")
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @NotNull(message = "WBS Polygon ID is required")
    @Column(name = "wbs_polygon_id", nullable = false)
    private UUID wbsPolygonId;

    @NotNull(message = "Capture date is required")
    @Column(name = "capture_date", nullable = false)
    private LocalDate captureDate;

    @Column(name = "satellite_image_id")
    private UUID satelliteImageId;

    @Column(name = "derived_progress_percent")
    private Double derivedProgressPercent;

    @Column(name = "contractor_claimed_percent")
    private Double contractorClaimedPercent;

    @Column(name = "variance_percent")
    private Double variancePercent;

    @NotNull(message = "Analysis method is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_method", nullable = false, length = 50)
    private ProgressAnalysisMethod analysisMethod;

    @Column(name = "remarks", length = 1000)
    private String remarks;
}
