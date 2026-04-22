package com.bipros.gis.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result of one SatelliteIngestionService.runForProject invocation. */
public record IngestionResult(
    UUID projectId,
    String vendorId,
    Instant runStartedAt,
    Instant runFinishedAt,
    int scenesFetched,
    int scenesSkippedDedupe,
    int snapshotsCreated,
    List<String> errors
) {}
