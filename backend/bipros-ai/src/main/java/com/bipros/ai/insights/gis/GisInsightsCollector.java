package com.bipros.ai.insights.gis;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.ai.insights.charts.EChartsOptions;
import com.bipros.ai.insights.dto.ChartSpec;
import com.bipros.gis.application.dto.ProgressVarianceResponse;
import com.bipros.gis.application.dto.SatelliteImageResponse;
import com.bipros.gis.application.dto.WbsPolygonResponse;
import com.bipros.gis.application.service.ConstructionProgressService;
import com.bipros.gis.application.service.SatelliteImageService;
import com.bipros.gis.application.service.WbsPolygonService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GisInsightsCollector implements InsightDataCollector {

    private final WbsPolygonService wbsPolygonService;
    private final SatelliteImageService satelliteImageService;
    private final ConstructionProgressService constructionProgressService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        List<WbsPolygonResponse> polygons = wbsPolygonService.getByProject(projectId);
        List<SatelliteImageResponse> images = satelliteImageService.getByProject(projectId);
        List<ProgressVarianceResponse> variances = constructionProgressService.getProgressVariance(projectId);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("polygonCount", polygons.size());
        root.put("satelliteImageCount", images.size());

        Optional<LocalDate> latestCapture = images.stream()
                .map(SatelliteImageResponse::captureDate)
                .filter(d -> d != null)
                .max(LocalDate::compareTo);
        root.put("latestImageCaptureDate", latestCapture.map(LocalDate::toString).orElse(null));

        double totalAreaSqM = polygons.stream()
                .map(WbsPolygonResponse::areaInSqMeters)
                .filter(a -> a != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        root.put("totalMappedAreaSqMeters", totalAreaSqM);

        Map<String, Long> sourceBreakdown = images.stream()
                .filter(i -> i.source() != null)
                .collect(Collectors.groupingBy(i -> i.source().name(), Collectors.counting()));
        ObjectNode sourceNode = root.putObject("imageSourceBreakdown");
        sourceBreakdown.forEach(sourceNode::put);

        Map<String, Long> imageStatusBreakdown = images.stream()
                .filter(i -> i.status() != null)
                .collect(Collectors.groupingBy(i -> i.status().name(), Collectors.counting()));
        ObjectNode imageStatusNode = root.putObject("imageStatusBreakdown");
        imageStatusBreakdown.forEach(imageStatusNode::put);

        Map<String, Long> varianceStatusBreakdown = variances.stream()
                .filter(v -> v.varianceStatus() != null)
                .collect(Collectors.groupingBy(ProgressVarianceResponse::varianceStatus, Collectors.counting()));
        ObjectNode varianceStatusNode = root.putObject("varianceStatusBreakdown");
        varianceStatusBreakdown.forEach(varianceStatusNode::put);

        List<ProgressVarianceResponse> topVariances = variances.stream()
                .filter(v -> v.variancePercent() != null)
                .sorted(Comparator.comparingDouble((ProgressVarianceResponse v) -> Math.abs(v.variancePercent())).reversed())
                .limit(10)
                .toList();

        ArrayNode varianceArray = root.putArray("topVariances");
        for (ProgressVarianceResponse v : topVariances) {
            ObjectNode node = varianceArray.addObject();
            node.put("wbsCode", v.wbsCode());
            node.put("wbsName", v.wbsName());
            node.put("derivedPercent", v.derivedPercent());
            node.put("claimedPercent", v.claimedPercent());
            node.put("variancePercent", v.variancePercent());
            node.put("varianceStatus", v.varianceStatus());
        }

        long unmappedPolygons = polygons.stream()
                .filter(p -> p.wbsNodeId() == null)
                .count();
        root.put("polygonsWithoutWbsLink", unmappedPolygons);

        long polygonsMissingArea = polygons.stream()
                .filter(p -> p.areaInSqMeters() == null || p.areaInSqMeters() <= 0)
                .count();
        root.put("polygonsMissingArea", polygonsMissingArea);

        return root;
    }

    @Override
    public List<ChartSpec> charts(UUID projectId) {
        if (projectId == null) {
            return List.of(
                    new ChartSpec("gis-variance-status", "Variance Status", "donut", null, null),
                    new ChartSpec("gis-top-variance", "Top Progress Variances", "bar", null, null),
                    new ChartSpec("gis-image-source", "Imagery by Source", "donut", null, null)
            );
        }

        List<SatelliteImageResponse> images = satelliteImageService.getByProject(projectId);
        List<ProgressVarianceResponse> variances = constructionProgressService.getProgressVariance(projectId);
        List<ChartSpec> charts = new ArrayList<>();

        Map<String, Long> varianceStatusBreakdown = variances.stream()
                .filter(v -> v.varianceStatus() != null)
                .collect(Collectors.groupingBy(ProgressVarianceResponse::varianceStatus, Collectors.counting()));
        charts.add(new ChartSpec("gis-variance-status", "Variance Status", "donut",
                EChartsOptions.donut(objectMapper, new LinkedHashMap<>(varianceStatusBreakdown)),
                "WBS zones grouped by progress-variance status"));

        List<ProgressVarianceResponse> topVariances = variances.stream()
                .filter(v -> v.variancePercent() != null)
                .sorted(Comparator.comparingDouble((ProgressVarianceResponse v) -> Math.abs(v.variancePercent())).reversed())
                .limit(8)
                .toList();
        charts.add(new ChartSpec("gis-top-variance", "Top Progress Variances", "bar",
                EChartsOptions.bar(objectMapper,
                        topVariances.stream().map(v -> v.wbsCode() != null ? v.wbsCode() : "").toList(),
                        "Variance %",
                        topVariances.stream().map(ProgressVarianceResponse::variancePercent).toList()),
                "Claimed vs imagery-derived variance, top 8 zones"));

        Map<String, Long> sourceBreakdown = images.stream()
                .filter(i -> i.source() != null)
                .collect(Collectors.groupingBy(i -> i.source().name(), Collectors.counting()));
        charts.add(new ChartSpec("gis-image-source", "Imagery by Source", "donut",
                EChartsOptions.donut(objectMapper, new LinkedHashMap<>(sourceBreakdown)),
                "Distribution of satellite-image sources"));

        return charts;
    }

    @Override
    public String tabKey() {
        return "gis";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights from the GIS map data: WBS polygons, satellite imagery, "
                + "and progress-variance comparisons (claimed vs imagery-derived). "
                + "Focus on zones where claimed progress diverges from satellite-derived progress, "
                + "imagery freshness gaps, and unmapped WBS items. Use the findings field for "
                + "non-numeric observations such as specific zones with significant variance, "
                + "missing imagery for active areas, or polygons without WBS links. "
                + "Recommendations should be operational (commission new imagery, audit suspicious "
                + "zones on site, link orphan polygons) rather than abstract.";
    }
}
