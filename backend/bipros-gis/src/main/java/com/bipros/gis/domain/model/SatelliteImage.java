package com.bipros.gis.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "satellite_images", schema = "gis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SatelliteImage extends BaseEntity {

    @NotNull(message = "Project ID is required")
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "layer_id")
    private UUID layerId;

    /** Scene identifier from spec (e.g. SCN-N03-250328). */
    @Column(name = "scene_id", unique = true, length = 80)
    private String sceneId;

    /** Cloud cover at capture time (0-100). */
    @Column(name = "cloud_cover_percent")
    private Double cloudCoverPercent;

    @NotBlank(message = "Image name is required")
    @Column(name = "image_name", nullable = false, length = 200)
    private String imageName;

    @Column(name = "description", length = 500)
    private String description;

    @NotNull(message = "Capture date is required")
    @Column(name = "capture_date", nullable = false)
    private LocalDate captureDate;

    @NotNull(message = "Source is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private SatelliteImageSource source;

    @Column(name = "resolution", length = 50)
    private String resolution;

    @Column(name = "bounding_box_geojson", columnDefinition = "TEXT")
    private String boundingBoxGeoJson;

    @NotBlank(message = "File path is required")
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @NotNull(message = "File size is required")
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @NotBlank(message = "MIME type is required")
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "north_bound")
    private Double northBound;

    @Column(name = "south_bound")
    private Double southBound;

    @Column(name = "east_bound")
    private Double eastBound;

    @Column(name = "west_bound")
    private Double westBound;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SatelliteImageStatus status = SatelliteImageStatus.UPLOADED;
}
