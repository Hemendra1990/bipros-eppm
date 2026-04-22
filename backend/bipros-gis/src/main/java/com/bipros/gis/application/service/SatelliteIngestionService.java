package com.bipros.gis.application.service;

import com.bipros.gis.application.dto.IngestionResult;
import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.model.SatelliteImageSource;
import com.bipros.gis.domain.model.SatelliteImageStatus;
import com.bipros.gis.domain.model.SatelliteSceneIngestionLog;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.SatelliteImageRepository;
import com.bipros.gis.domain.repository.SatelliteSceneIngestionLogRepository;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import com.bipros.integration.adapter.satellite.SatelliteAdapter;
import com.bipros.integration.adapter.satellite.SatelliteAdapterRegistry;
import com.bipros.integration.adapter.satellite.SceneDescriptor;
import com.bipros.integration.storage.RasterStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates one satellite ingestion run for a project. Designed to be:
 * <ul>
 *   <li><b>idempotent</b> — re-running over the same date range is a no-op
 *       because {@code satellite_images.scene_id} is unique and the repo's
 *       {@code existsBySceneId} dedupes before write.</li>
 *   <li><b>resilient</b> — one polygon or scene failing doesn't abort the run;
 *       errors are collected and surfaced via {@link IngestionResult} + the
 *       {@link SatelliteSceneIngestionLog} row.</li>
 *   <li><b>analyzer-agnostic</b> — this class does NOT run AI analysis. Phase 3
 *       will add {@code ProgressAnalyzerService.analyze(...)} as an
 *       {@code @Async} callback after each SatelliteImage is persisted.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SatelliteIngestionService {

    private final WbsPolygonRepository polygonRepository;
    private final SatelliteImageRepository imageRepository;
    private final SatelliteSceneIngestionLogRepository logRepository;
    private final SatelliteAdapterRegistry adapterRegistry;
    private final RasterStorage rasterStorage;
    private final ProgressAnalyzerService analyzerService;

    @Transactional
    public IngestionResult runForProject(UUID projectId, LocalDate from, LocalDate to) {
        Instant started = Instant.now();
        SatelliteAdapter adapter = adapterRegistry.defaultAdapter();

        SatelliteSceneIngestionLog auditLog = SatelliteSceneIngestionLog.builder()
            .projectId(projectId)
            .vendorId(adapter.vendorId())
            .fromDate(from)
            .toDate(to)
            .runStartedAt(started)
            .status(SatelliteSceneIngestionLog.Status.RUNNING)
            .build();
        auditLog = logRepository.save(auditLog);

        List<WbsPolygon> polygons = polygonRepository.findByProjectId(projectId);
        int scenesFetched = 0;
        int scenesSkipped = 0;
        int snapshotsCreated = 0;
        List<String> errors = new ArrayList<>();

        // A single scene can overlap many polygons in a project. Download once;
        // record the SatelliteImage once. Per-polygon analysis is Phase 3's job.
        Map<String, SatelliteImage> seenThisRun = new HashMap<>();

        for (WbsPolygon polygon : polygons) {
            List<SceneDescriptor> descriptors;
            try {
                descriptors = adapter.findImagery(polygon.getPolygon(), from, to);
            } catch (Exception e) {
                log.warn("[Ingestion] findImagery failed for polygon {}: {}",
                    polygon.getId(), e.getMessage());
                errors.add("findImagery(polygon=" + polygon.getWbsCode() + "): " + e.getMessage());
                continue;
            }

            for (SceneDescriptor desc : descriptors) {
                if (seenThisRun.containsKey(desc.sceneId())) continue;
                if (imageRepository.existsBySceneId(desc.sceneId())) {
                    scenesSkipped++;
                    continue;
                }
                try {
                    byte[] raster = adapter.fetchRaster(desc.sceneId(), polygon.getPolygon(), 1024);
                    String key = projectId + "/" + desc.captureDate().getYear() + "/"
                        + String.format("%02d", desc.captureDate().getMonthValue()) + "/"
                        + sanitise(desc.sceneId()) + ".tif";
                    URI storedAt = rasterStorage.put(key, raster, adapter.rasterContentType());
                    SatelliteImage image = persistImage(projectId, polygon.getLayerId(), desc, storedAt,
                        raster.length, adapter.rasterContentType());
                    seenThisRun.put(desc.sceneId(), image);
                    scenesFetched++;

                    // Fire the analyzer asynchronously. analyzeAsync() is an
                    // @Async method, so this returns immediately; the snapshot
                    // lands later via its own REQUIRES_NEW transaction.
                    try {
                        analyzerService.analyzeAsync(image, polygon, null);
                        snapshotsCreated++;
                    } catch (Exception e) {
                        log.warn("[Ingestion] analyzer dispatch failed for image {}: {}",
                            image.getId(), e.getMessage());
                        errors.add("analyze(scene=" + desc.sceneId() + "): " + e.getMessage());
                    }
                } catch (Exception e) {
                    log.warn("[Ingestion] fetch/store failed for scene {}: {}", desc.sceneId(), e.getMessage());
                    errors.add("fetch(scene=" + desc.sceneId() + "): " + e.getMessage());
                }
            }
        }

        Instant finished = Instant.now();
        auditLog.setRunFinishedAt(finished);
        auditLog.setScenesFetched(scenesFetched);
        auditLog.setSnapshotsCreated(snapshotsCreated);
        auditLog.setStatus(errors.isEmpty() ? SatelliteSceneIngestionLog.Status.COMPLETED
            : (scenesFetched > 0 ? SatelliteSceneIngestionLog.Status.PARTIAL
                                  : SatelliteSceneIngestionLog.Status.FAILED));
        auditLog.setErrorsJson(errors.isEmpty() ? null : "[\"" + String.join("\",\"", errors) + "\"]");
        auditLog.setMetadataJson("{\"scenesSkippedDedupe\":" + scenesSkipped + "}");
        logRepository.save(auditLog);

        log.info("[Ingestion] project={} vendor={} fetched={} skipped={} errors={} in {}ms",
            projectId, adapter.vendorId(), scenesFetched, scenesSkipped, errors.size(),
            java.time.Duration.between(started, finished).toMillis());

        return new IngestionResult(projectId, adapter.vendorId(), started, finished,
            scenesFetched, scenesSkipped, snapshotsCreated, errors);
    }

    private SatelliteImage persistImage(UUID projectId, UUID layerId, SceneDescriptor desc,
                                        URI storedAt, int fileSize, String mimeType) {
        SatelliteImage image = new SatelliteImage();
        image.setProjectId(projectId);
        image.setLayerId(layerId);
        image.setSceneId(desc.sceneId());
        image.setImageName(desc.vendorId() + " " + desc.captureDate() + " " + desc.sceneId());
        image.setCaptureDate(desc.captureDate());
        image.setSource(resolveSource(desc.vendorId()));
        image.setCloudCoverPercent(desc.cloudCoverPercent());
        image.setFilePath(storedAt.toString());
        image.setFileSize((long) fileSize);
        image.setMimeType(mimeType);
        image.setStatus(SatelliteImageStatus.READY);
        if (desc.bbox() != null) {
            Envelope env = desc.bbox().getEnvelopeInternal();
            image.setWestBound(env.getMinX());
            image.setEastBound(env.getMaxX());
            image.setSouthBound(env.getMinY());
            image.setNorthBound(env.getMaxY());
        }
        return imageRepository.save(image);
    }

    private SatelliteImageSource resolveSource(String vendorId) {
        return switch (vendorId) {
            case "sentinel-hub" -> SatelliteImageSource.SENTINEL_HUB;
            case "planet-labs" -> SatelliteImageSource.PLANET_LABS;
            case "maxar" -> SatelliteImageSource.MAXAR;
            case "airbus" -> SatelliteImageSource.AIRBUS;
            case "isro-cartosat" -> SatelliteImageSource.ISRO_CARTOSAT;
            default -> SatelliteImageSource.MANUAL_UPLOAD;
        };
    }

    private String sanitise(String sceneId) {
        // Scene IDs can contain slashes, colons, dots. Make them filesystem-safe.
        return sceneId.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /** Small ignoring of the unused ref for Polygon import warning in older IDEs. */
    private void __unused(Polygon p) { /* noop */ }
}
