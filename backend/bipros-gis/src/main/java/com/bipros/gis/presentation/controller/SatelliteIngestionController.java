package com.bipros.gis.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.gis.application.dto.IngestionResult;
import com.bipros.gis.application.service.SatelliteIngestionService;
import com.bipros.gis.domain.model.SatelliteSceneIngestionLog;
import com.bipros.gis.domain.repository.SatelliteSceneIngestionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/gis")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class SatelliteIngestionController {

    private final SatelliteIngestionService ingestionService;
    private final SatelliteSceneIngestionLogRepository logRepository;

    /**
     * Manual trigger used by the UI "Run Ingestion" button. Runs synchronously
     * and returns when all polygons have been processed. Typical duration:
     * 30-90 s for a 10-polygon project. For nightly runs, use the
     * SatelliteIngestionScheduler (Phase 4).
     */
    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<IngestionResult>> ingest(
        @PathVariable UUID projectId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        IngestionResult result = ingestionService.runForProject(projectId, from, to);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** Recent ingestion runs for a project, newest first. Used by the UI's "last sync" indicator. */
    @GetMapping("/ingestion-log")
    public ResponseEntity<ApiResponse<List<SatelliteSceneIngestionLog>>> log(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(
            logRepository.findByProjectIdOrderByRunStartedAtDesc(projectId)));
    }
}
