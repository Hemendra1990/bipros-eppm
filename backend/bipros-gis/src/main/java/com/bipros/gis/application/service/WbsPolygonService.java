package com.bipros.gis.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.gis.application.dto.GeoJsonFeature;
import com.bipros.gis.application.dto.GeoJsonFeatureCollection;
import com.bipros.gis.application.dto.WbsPolygonRequest;
import com.bipros.gis.application.dto.WbsPolygonResponse;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WbsPolygonService {

    private final WbsPolygonRepository polygonRepository;
    private final ObjectMapper objectMapper;

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
        polygon.setPolygon(parsePolygon(request.polygonGeoJson()));
        polygon.setCenterLatitude(request.centerLatitude());
        polygon.setCenterLongitude(request.centerLongitude());
        if (request.areaInSqMeters() != null) polygon.setAreaInSqMeters(request.areaInSqMeters());
        if (request.fillColor() != null) polygon.setFillColor(request.fillColor());
        if (request.strokeColor() != null) polygon.setStrokeColor(request.strokeColor());

        WbsPolygon updated = polygonRepository.save(polygon);
        return WbsPolygonResponse.from(updated);
    }

    public void delete(UUID projectId, UUID polygonId) {
        WbsPolygon polygon = polygonRepository.findById(polygonId)
            .filter(p -> p.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("WbsPolygon", polygonId.toString()));
        polygonRepository.delete(polygon);
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
