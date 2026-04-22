package com.bipros.gis.application.service;

import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Nightly ingestion trigger. Wakes up at the cron time set in
 * {@code bipros.satellite.ingestion.cron} (default 03:00), finds every project
 * with at least one WbsPolygon, and runs a last-N-days ingestion for each.
 * <p>
 * Disabled by default via {@link ConditionalOnProperty} so dev doesn't spam
 * Sentinel Hub / Anthropic when running. Flip
 * {@code bipros.satellite.ingestion.enabled=true} to opt in.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "bipros.satellite.ingestion.enabled", havingValue = "true")
public class SatelliteIngestionScheduler {

    private final WbsPolygonRepository polygonRepository;
    private final SatelliteIngestionService ingestionService;

    @Value("${bipros.satellite.ingestion.max-days-lookback:7}")
    private int maxDaysLookback;

    @Scheduled(cron = "${bipros.satellite.ingestion.cron:0 0 3 * * *}")
    public void runNightly() {
        log.info("[Ingestion] nightly scheduler start (lookback={} days)", maxDaysLookback);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(maxDaysLookback);

        Set<UUID> projectIds = new HashSet<>();
        for (WbsPolygon p : polygonRepository.findAll()) {
            projectIds.add(p.getProjectId());
        }
        int ok = 0;
        int failed = 0;
        for (UUID projectId : projectIds) {
            try {
                ingestionService.runForProject(projectId, from, to);
                ok++;
            } catch (Exception e) {
                log.warn("[Ingestion] project {} failed: {}", projectId, e.getMessage());
                failed++;
            }
        }
        log.info("[Ingestion] nightly done: {} ok / {} failed over {} projects",
            ok, failed, projectIds.size());
    }
}
