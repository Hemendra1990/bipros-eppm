package com.bipros.gis.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "wbs_polygons", schema = "gis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WbsPolygon extends BaseEntity {

    @NotNull(message = "Project ID is required")
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @NotNull(message = "WBS Node ID is required")
    @Column(name = "wbs_node_id", nullable = false)
    private UUID wbsNodeId;

    @NotNull(message = "Layer ID is required")
    @Column(name = "layer_id", nullable = false)
    private UUID layerId;

    @NotBlank(message = "WBS code is required")
    @Column(name = "wbs_code", nullable = false, length = 50)
    private String wbsCode;

    @NotBlank(message = "WBS name is required")
    @Column(name = "wbs_name", nullable = false, length = 200)
    private String wbsName;

    @NotNull(message = "Polygon GeoJSON is required")
    @Column(name = "polygon_geojson", nullable = false, columnDefinition = "TEXT")
    private String polygonGeoJson;

    @NotNull(message = "Center latitude is required")
    @Column(name = "center_latitude", nullable = false)
    private Double centerLatitude;

    @NotNull(message = "Center longitude is required")
    @Column(name = "center_longitude", nullable = false)
    private Double centerLongitude;

    @Column(name = "area_in_sq_meters")
    private Double areaInSqMeters;

    @Column(name = "fill_color", nullable = false, length = 7)
    private String fillColor = "#3388ff";

    @Column(name = "stroke_color", nullable = false, length = 7)
    private String strokeColor = "#000000";
}
