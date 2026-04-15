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

    @Column(name = "wbs_polygon_id")
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

    /** AI-estimated physical progress % (M3 spec). */
    @Column(name = "ai_progress_percent")
    private Double aiProgressPercent;

    /** Construction Visibility Index — 0-100. */
    @Column(name = "cvi")
    private Double cvi;

    /** Earthwork Detection Index — 0-100. */
    @Column(name = "edi")
    private Double edi;

    /** NDVI change since prior scene (−1 to +1). */
    @Column(name = "ndvi_change")
    private Double ndviChange;

    /** Denormalised WBS package code (e.g. DMIC-N03-P01) — matches Contract.wbsPackageCode. */
    @Column(name = "wbs_package_code", length = 60)
    private String wbsPackageCode;

    /** Derived alert banding (GREEN/AMBER/RED variants). */
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_flag", length = 40)
    private SatelliteAlertFlag alertFlag;

    @NotNull(message = "Analysis method is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_method", nullable = false, length = 50)
    private ProgressAnalysisMethod analysisMethod;

    @Column(name = "remarks", length = 1000)
    private String remarks;
}
