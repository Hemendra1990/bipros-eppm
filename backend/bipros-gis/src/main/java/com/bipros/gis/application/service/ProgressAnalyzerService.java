package com.bipros.gis.application.service;

import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.model.ProgressAnalysisMethod;
import com.bipros.gis.domain.model.SatelliteAlertFlag;
import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import com.bipros.integration.adapter.ai.AnalysisRequest;
import com.bipros.integration.adapter.ai.AnalysisResult;
import com.bipros.integration.adapter.ai.ProgressAnalyzer;
import com.bipros.integration.adapter.ai.ProgressAnalyzerRegistry;
import com.bipros.integration.storage.RasterStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges an ingested SatelliteImage + WbsPolygon into a ConstructionProgressSnapshot by
 * routing the raster through the configured {@link ProgressAnalyzer} (Claude vision by
 * default). Runs on a Spring TaskExecutor so the ingestion pipeline never blocks on a
 * slow LLM call.
 * <p>
 * If no analyzer is configured (e.g. dev profile with provider=none), this method logs a
 * debug line and returns without creating a snapshot — cheap no-op.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressAnalyzerService {

    private final ConstructionProgressSnapshotRepository snapshotRepository;
    private final ProgressAnalyzerRegistry analyzerRegistry;
    private final RasterStorage rasterStorage;

    /**
     * Run analysis and persist a ConstructionProgressSnapshot. Propagation REQUIRES_NEW so the
     * surrounding ingestion transaction isn't affected by analyzer errors — each snapshot row
     * commits (or rolls back) independently.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analyzeAsync(SatelliteImage image, WbsPolygon polygon, Double contractorClaimedPercent) {
        Optional<ProgressAnalyzer> maybeAnalyzer = analyzerRegistry.preferred();
        if (maybeAnalyzer.isEmpty()) {
            log.debug("[Analyzer] no analyzer registered; skipping image={} polygon={}",
                image.getId(), polygon.getId());
            return;
        }
        ProgressAnalyzer analyzer = maybeAnalyzer.get();

        byte[] raster;
        try {
            raster = rasterStorage.get(URI.create(image.getFilePath()));
        } catch (Exception e) {
            log.warn("[Analyzer] raster fetch failed for image {}: {}", image.getId(), e.getMessage());
            return;
        }

        AnalysisResult result = analyzer.analyze(new AnalysisRequest(
            raster,
            image.getMimeType(),
            polygon.getWbsCode(),
            polygon.getWbsName(),
            contractorClaimedPercent,
            image.getCaptureDate()));

        ConstructionProgressSnapshot snapshot = new ConstructionProgressSnapshot();
        snapshot.setProjectId(image.getProjectId());
        snapshot.setWbsPolygonId(polygon.getId());
        snapshot.setSatelliteImageId(image.getId());
        snapshot.setCaptureDate(image.getCaptureDate() != null ? image.getCaptureDate() : LocalDate.now());
        snapshot.setAiProgressPercent(result.progressPercent());
        snapshot.setDerivedProgressPercent(result.progressPercent());
        snapshot.setContractorClaimedPercent(contractorClaimedPercent);
        snapshot.setCvi(result.cvi());
        snapshot.setEdi(result.edi());
        snapshot.setNdviChange(result.ndviChange());
        snapshot.setWbsPackageCode(polygon.getWbsCode());
        snapshot.setAnalysisMethod(ProgressAnalysisMethod.AI_SEGMENTATION);
        snapshot.setAnalyzerId(result.analyzerId());
        snapshot.setAnalysisDurationMs((int) Math.min(Integer.MAX_VALUE, result.durationMs()));
        snapshot.setAnalysisCostMicros(result.costMicros());
        snapshot.setRemarks(result.remarks());
        snapshot.setAlertFlag(deriveAlert(result.progressPercent(), contractorClaimedPercent));
        // Variance is |claimed - ai|; set only when both present.
        if (result.progressPercent() != null && contractorClaimedPercent != null) {
            snapshot.setVariancePercent(Math.abs(contractorClaimedPercent - result.progressPercent()));
        }

        snapshotRepository.save(snapshot);
        log.info("[Analyzer] snapshot for polygon={} progress={}% variance={}% duration={}ms",
            polygon.getWbsCode(),
            result.progressPercent(),
            snapshot.getVariancePercent(),
            result.durationMs());
    }

    /**
     * Classic RAG gate matching the SatelliteAlertFlag enum levels: ≤5% GREEN,
     * 5–10% AMBER, &gt;10% RED. No variance data → null (no false GREEN).
     */
    private SatelliteAlertFlag deriveAlert(Double ai, Double claimed) {
        if (ai == null || claimed == null) return null;
        double v = Math.abs(ai - claimed);
        if (v <= 5.0) return SatelliteAlertFlag.GREEN;
        if (v <= 10.0) return SatelliteAlertFlag.AMBER_VARIANCE_GT5;
        return SatelliteAlertFlag.RED_VARIANCE_GT10;
    }
}
