package com.bipros.api.config.seeder;

import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.model.GisLayer;
import com.bipros.gis.domain.model.GisLayerType;
import com.bipros.gis.domain.model.ProgressAnalysisMethod;
import com.bipros.gis.domain.model.SatelliteAlertFlag;
import com.bipros.gis.domain.model.SatelliteSceneIngestionLog;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import com.bipros.gis.domain.repository.GisLayerRepository;
import com.bipros.gis.domain.repository.SatelliteSceneIngestionLogRepository;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(155)
@RequiredArgsConstructor
public class OmanGisSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final GisLayerRepository gisLayerRepository;
    private final WbsPolygonRepository wbsPolygonRepository;
    private final SatelliteSceneIngestionLogRepository satelliteSceneIngestionLogRepository;
    private final ConstructionProgressSnapshotRepository constructionProgressSnapshotRepository;

    private static final String[] STRETCH_CODES = {"BNK-S1", "BNK-S2", "BNK-S3", "BNK-S4"};
    private static final String[] STRETCH_NAMES = {
        "Barka Junction \u2192 Wadi Al Hattat",
        "Wadi Al Hattat \u2192 Mid-corridor Bridge",
        "Mid-corridor Bridge \u2192 Nakhal Approach",
        "Nakhal Approach \u2192 Nakhal Roundabout"
    };

    @Override
    public void run(String... args) {
        Optional<Project> projectOpt = projectRepository.findByCode(PROJECT_CODE);
        if (projectOpt.isEmpty()) {
            log.warn("[BNK-GIS] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        Project project = projectOpt.get();
        UUID projectId = project.getId();

        if (!gisLayerRepository.findByProjectIdOrderBySortOrder(projectId).isEmpty()) {
            log.info("[BNK-GIS] GIS layers already present for project '{}' — skipping", PROJECT_CODE);
            return;
        }

        Random rng = new Random(DETERMINISTIC_SEED);
        GeometryFactory gf = new GeometryFactory();

        List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
        List<WbsNode> leafNodes = wbsNodes.stream()
            .filter(n -> wbsNodes.stream().noneMatch(c -> n.getId().equals(c.getParentId())))
            .toList();

        GisLayer layer = seedGisLayer(projectId);
        int polygonCount = seedWbsPolygons(projectId, layer.getId(), leafNodes, gf);
        SatelliteSceneIngestionLog logEntry = seedIngestionLog(projectId);
        int snapshotCount = seedConstructionProgressSnapshots(projectId, leafNodes, rng);

        log.info("[BNK-GIS] Seeded 1 GisLayer, {} WBS polygons, 1 ingestion log, {} progress snapshots",
            polygonCount, snapshotCount);
    }

    private GisLayer seedGisLayer(UUID projectId) {
        GisLayer layer = new GisLayer();
        layer.setProjectId(projectId);
        layer.setLayerName("Barka-Nakhal Corridor Centreline");
        layer.setLayerType(GisLayerType.WBS_POLYGON);
        layer.setDescription("41 km Barka\u2013Nakhal road corridor divided into 4 construction stretches (BNK-S1 through BNK-S4)");
        layer.setIsVisible(Boolean.TRUE);
        layer.setOpacity(0.8);
        layer.setSortOrder(0);
        return gisLayerRepository.save(layer);
    }

    private int seedWbsPolygons(UUID projectId, UUID layerId, List<WbsNode> leafNodes, GeometryFactory gf) {
        double[][] corridorCoords = {
            {23.7100, 57.8900, 23.6900, 57.8700},
            {23.6900, 57.8700, 23.6750, 57.8550},
            {23.6750, 57.8550, 23.6600, 57.8420},
            {23.6600, 57.8420, 23.6500, 57.8300}
        };

        int created = 0;
        for (int i = 0; i < STRETCH_CODES.length; i++) {
            String stretchCode = STRETCH_CODES[i];
            WbsNode matchingNode = leafNodes.stream()
                .filter(n -> n.getCode() != null && n.getCode().contains(stretchCode.replace("BNK-", "")))
                .findFirst()
                .orElse(leafNodes.size() > i ? leafNodes.get(i) : null);

            if (matchingNode == null) continue;

            double northLat = corridorCoords[i][0];
            double eastLon = corridorCoords[i][1];
            double southLat = corridorCoords[i][2];
            double westLon = corridorCoords[i][3];

            double expand = 0.005;
            Coordinate[] coords = {
                new Coordinate(westLon - expand, southLat - expand),
                new Coordinate(eastLon + expand, southLat - expand),
                new Coordinate(eastLon + expand, northLat + expand),
                new Coordinate(westLon - expand, northLat + expand),
                new Coordinate(westLon - expand, southLat - expand)
            };
            LinearRing shell = new LinearRing(new CoordinateArraySequence(coords), gf);
            Polygon polygon = new Polygon(shell, null, gf);

            double centerLat = (northLat + southLat) / 2.0;
            double centerLon = (eastLon + westLon) / 2.0;
            double areaSqM = Math.abs((eastLon - westLon) * (northLat - southLat)) * 111_000 * 111_000
                * Math.cos(Math.toRadians(centerLat));

            WbsPolygon wp = new WbsPolygon();
            wp.setProjectId(projectId);
            wp.setWbsNodeId(matchingNode.getId());
            wp.setLayerId(layerId);
            wp.setWbsCode(stretchCode);
            wp.setWbsName(STRETCH_NAMES[i]);
            wp.setPolygon(polygon);
            wp.setCenterLatitude(centerLat);
            wp.setCenterLongitude(centerLon);
            wp.setAreaInSqMeters(areaSqM);
            wp.setFillColor("#3388ff");
            wp.setStrokeColor("#000000");
            wbsPolygonRepository.save(wp);
            created++;
        }
        return created;
    }

    private SatelliteSceneIngestionLog seedIngestionLog(UUID projectId) {
        Instant now = Instant.now();
        SatelliteSceneIngestionLog entry = SatelliteSceneIngestionLog.builder()
            .projectId(projectId)
            .vendorId("SENTINEL-2")
            .fromDate(DEFAULT_DATA_DATE.minusDays(30))
            .toDate(DEFAULT_DATA_DATE)
            .runStartedAt(now.minusSeconds(7200))
            .runFinishedAt(now.minusSeconds(6900))
            .scenesFetched(8)
            .snapshotsCreated(5)
            .status(SatelliteSceneIngestionLog.Status.COMPLETED)
            .metadataJson("{\"provider\":\"Sentinel Hub\",\"cloudCoverMax\":20,\"bands\":[\"B04\",\"B08\",\"NDVI\"]}")
            .build();
        return satelliteSceneIngestionLogRepository.save(entry);
    }

    private int seedConstructionProgressSnapshots(UUID projectId, List<WbsNode> leafNodes, Random rng) {
        if (leafNodes.isEmpty()) return 0;
        int created = 0;
        for (int d = 0; d < 5; d++) {
            LocalDate captureDate = DEFAULT_DATA_DATE.minusDays(d * 7L);
            for (int n = 0; n < Math.min(leafNodes.size(), 4); n++) {
                WbsNode node = leafNodes.get(n);
                double aiProgress = 20 + rng.nextDouble() * 50;
                double contractorClaimed = aiProgress + 3 + rng.nextDouble() * 5;
                double variance = contractorClaimed - aiProgress;
                double cvi = 40 + rng.nextDouble() * 50;
                double edi = 30 + rng.nextDouble() * 40;
                double ndviChange = -0.1 + rng.nextDouble() * 0.3;

                SatelliteAlertFlag alertFlag;
                if (Math.abs(variance) <= 5) alertFlag = SatelliteAlertFlag.GREEN;
                else if (Math.abs(variance) <= 10) alertFlag = SatelliteAlertFlag.AMBER_VARIANCE_GT5;
                else alertFlag = SatelliteAlertFlag.RED_VARIANCE_GT10;

                ConstructionProgressSnapshot snap = new ConstructionProgressSnapshot();
                snap.setProjectId(projectId);
                snap.setWbsPolygonId(null);
                snap.setCaptureDate(captureDate);
                snap.setDerivedProgressPercent(round2(aiProgress));
                snap.setContractorClaimedPercent(round2(contractorClaimed));
                snap.setVariancePercent(round2(variance));
                snap.setAiProgressPercent(round2(aiProgress));
                snap.setCvi(round2(cvi));
                snap.setEdi(round2(edi));
                snap.setNdviChange(round3(ndviChange));
                snap.setWbsPackageCode(node.getCode());
                snap.setAlertFlag(alertFlag);
                snap.setAnalysisMethod(ProgressAnalysisMethod.AI_SEGMENTATION);
                snap.setAnalyzerId("claude-vision:claude-sonnet-4-6");
                snap.setAnalysisDurationMs(1500 + rng.nextInt(3000));
                snap.setAnalysisCostMicros(2500L + rng.nextInt(5000));
                snap.setRemarks("Satellite scene " + (d + 1) + " for " + node.getCode());
                constructionProgressSnapshotRepository.save(snap);
                created++;
            }
        }
        return created;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
