package com.bipros.gis.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Polygon;

import java.util.UUID;

/**
 * WBS package boundary as a PostGIS geometry. Stored in EPSG:4326 (WGS84 lat/lon)
 * so satellite imagery — which Sentinel Hub serves in 4326 by default — aligns
 * without a reprojection step. GeoJSON in and out of the service layer is handled
 * by {@link com.bipros.gis.application.service.WbsPolygonService} via JTS
 * GeoJsonReader/Writer; the entity itself carries the native geometry.
 */
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

    /**
     * Polygon geometry in EPSG:4326. hibernate-spatial maps this to
     * {@code geometry(Polygon, 4326)} in PostGIS.
     */
    @NotNull(message = "Polygon is required")
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "polygon", nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon polygon;

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
