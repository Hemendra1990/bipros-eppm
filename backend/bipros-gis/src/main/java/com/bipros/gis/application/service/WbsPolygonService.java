package com.bipros.gis.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.gis.application.dto.GeoJsonFeature;
import com.bipros.gis.application.dto.GeoJsonFeatureCollection;
import com.bipros.gis.application.dto.WbsPolygonRequest;
import com.bipros.gis.application.dto.WbsPolygonResponse;
import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import com.bipros.gis.domain.repository.SatelliteImageRepository;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import com.bipros.integration.storage.RasterStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WbsPolygonService {

    private final WbsPolygonRepository polygonRepository;
    private final ObjectMapper objectMapper;
    private final SatelliteImageRepository imageRepository;
    private final ConstructionProgressSnapshotRepository snapshotRepository;
    private final RasterStorage rasterStorage;

    /** JTS GeoJsonReader and Writer are thread-safe for simple reads/writes. */
    private static final GeoJsonReader GEOJSON_READER = new GeoJsonReader();
    private static final GeoJsonWriter GEOJSON_WRITER = new GeoJsonWriter();
    static { GEOJSON_WRITER.setEncodeCRS(false); }

    public WbsPolygonResponse create(UUID projectId, WbsPolygonRequest request) {
        WbsPolygon polygon = new WbsPolygon();
        polygon.setProjectId(projectId);
        polygon.setWbsNodeId(request.wbsNodeId());
        polygon.setLayerId(request.layerId());
        polygon.setWbsCode(request.wbsCode());
        polygon.setWbsName(request.wbsName());
        polygon.setName(request.name());
        polygon.setPolygon(parsePolygon(request.polygonGeoJson()));
        polygon.setCenterLatitude(request.centerLatitude());
        polygon.setCenterLongitude(request.centerLongitude());
        polygon.setAreaInSqMeters(request.areaInSqMeters());
        polygon.setFillColor(request.fillColor() != null ? request.fillColor() : "#3388ff");
        polygon.setStrokeColor(request.strokeColor() != null ? request.strokeColor() : "#000000");

        WbsPolygon saved = polygonRepository.save(polygon);
        return WbsPolygonResponse.from(saved);
    }

    public WbsPolygonResponse getById(UUID projectId, UUID polygonId) {
        WbsPolygon polygon = polygonRepository.findById(polygonId)
            .filter(p -> p.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("WbsPolygon", polygonId.toString()));
        return WbsPolygonResponse.from(polygon);
    }

    public List<WbsPolygonResponse> getByProject(UUID projectId) {
        return polygonRepository.findByProjectId(projectId)
            .stream()
            .map(WbsPolygonResponse::from)
            .toList();
    }

    public WbsPolygonResponse update(UUID projectId, UUID polygonId, WbsPolygonRequest request) {
        WbsPolygon polygon = polygonRepository.findById(polygonId)
            .filter(p -> p.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("WbsPolygon", polygonId.toString()));

        polygon.setWbsCode(request.wbsCode());
        polygon.setWbsName(request.wbsName());
        polygon.setName(request.name());
        polygon.setPolygon(parsePolygon(request.polygonGeoJson()));
        polygon.setCenterLatitude(request.centerLatitude());
        polygon.setCenterLongitude(request.centerLongitude());
        if (request.areaInSqMeters() != null) polygon.setAreaInSqMeters(request.areaInSqMeters());
        if (request.fillColor() != null) polygon.setFillColor(request.fillColor());
        if (request.strokeColor() != null) polygon.setStrokeColor(request.strokeColor());

        WbsPolygon updated = polygonRepository.save(polygon);
        return WbsPolygonResponse.from(updated);
    }

    /**
     * Cascade delete: removes the polygon plus everything it owns — its
     * satellite scenes and their MinIO rasters, legacy (null-polygon) scenes
     * whose footprint overlaps it, and its analysis snapshots. Raster deletes
     * are best-effort (a missing/unreachable object never blocks the DB delete).
     */
    @Transactional
    public void delete(UUID projectId, UUID polygonId) {
        WbsPolygon polygon = polygonRepository.findById(polygonId)
            .filter(p -> p.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("WbsPolygon", polygonId.toString()));

        // Owned scenes + their rasters.
        deleteImagesWithRasters(imageRepository.findByWbsPolygonId(polygonId));

        // Legacy (null-polygon) scenes whose footprint bbox overlaps this polygon.
        Envelope polygonEnv = polygon.getPolygon().getEnvelopeInternal();
        List<SatelliteImage> legacyOverlaps = imageRepository.findByProjectIdOrderByCaptureDate(projectId).stream()
            .filter(img -> img.getWbsPolygonId() == null)
            .filter(img -> overlaps(img, polygonEnv))
            .toList();
        deleteImagesWithRasters(legacyOverlaps);

        // Analysis snapshots owned by the polygon.
        snapshotRepository.deleteAll(snapshotRepository.findByWbsPolygonIdOrderByCaptureDate(polygonId));

        // The polygon row itself.
        polygonRepository.delete(polygon);
    }

    /** Multi-select delete — runs the single-polygon cascade per id; returns the count deleted. */
    @Transactional
    public int deleteBatch(UUID projectId, List<UUID> ids) {
        int deleted = 0;
        for (UUID id : ids) {
            delete(projectId, id);
            deleted++;
        }
        return deleted;
    }

    /** Delete image rows and best-effort remove their backing rasters. */
    private void deleteImagesWithRasters(List<SatelliteImage> images) {
        if (images.isEmpty()) return;
        for (SatelliteImage img : images) {
            if (img.getFilePath() == null) continue;
            try {
                rasterStorage.delete(URI.create(img.getFilePath()));
            } catch (Exception e) {
                log.warn("[Polygon delete] raster delete failed for image {} ({}): {}",
                    img.getId(), img.getFilePath(), e.getMessage());
            }
        }
        imageRepository.deleteAll(images);
    }

    /** Standard bbox intersection between a legacy image footprint and the polygon envelope. */
    private boolean overlaps(SatelliteImage img, Envelope polygonEnv) {
        if (img.getWestBound() == null || img.getEastBound() == null
            || img.getSouthBound() == null || img.getNorthBound() == null) {
            return false;
        }
        Envelope imageEnv = new Envelope(
            img.getWestBound(), img.getEastBound(),
            img.getSouthBound(), img.getNorthBound());
        return polygonEnv.intersects(imageEnv);
    }

    public GeoJsonFeatureCollection getAsGeoJson(UUID projectId) {
        List<WbsPolygon> polygons = polygonRepository.findByProjectId(projectId);

        List<GeoJsonFeature> features = polygons.stream()
            .map(polygon -> {
                try {
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("wbsCode", polygon.getWbsCode());
                    properties.put("wbsName", polygon.getWbsName());
                    properties.put("wbsNodeId", polygon.getWbsNodeId().toString());
                    properties.put("fillColor", polygon.getFillColor());
                    properties.put("strokeColor", polygon.getStrokeColor());
                    properties.put("id", polygon.getId().toString());

                    JsonNode geometry = objectMapper.readTree(GEOJSON_WRITER.write(polygon.getPolygon()));
                    return GeoJsonFeature.create(properties, geometry);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to render GeoJSON for polygon: " + polygon.getId(), e);
                }
            })
            .toList();

        return GeoJsonFeatureCollection.create(features);
    }

    /**
     * Parse a GeoJSON string into a JTS {@link Polygon}. Rejects anything that
     * isn't a single Polygon (MultiPolygon, Point, etc.) with a clear
     * business-rule error — this module models one boundary per WBS node.
     */
    private Polygon parsePolygon(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            throw new BusinessRuleException("INVALID_POLYGON", "Polygon GeoJSON is empty");
        }
        try {
            Geometry geometry = GEOJSON_READER.read(geoJson);
            if (!(geometry instanceof Polygon poly)) {
                throw new BusinessRuleException("INVALID_POLYGON",
                    "Only Polygon geometry is supported (got " + geometry.getGeometryType() + ")");
            }
            if (poly.getSRID() == 0) poly.setSRID(4326);
            return poly;
        } catch (ParseException e) {
            throw new BusinessRuleException("INVALID_POLYGON",
                "Failed to parse GeoJSON: " + e.getMessage());
        }
    }
}
