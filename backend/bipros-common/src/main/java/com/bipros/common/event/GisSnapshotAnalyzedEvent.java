package com.bipros.common.event;

import java.util.UUID;

/**
 * Published when a GIS satellite/drone snapshot finishes AI progress analysis. Consumed by the GIS
 * Intelligence agent trigger path (wiring the publisher into ProgressAnalyzerService is a follow-up;
 * the nightly sweep is the safe fallback until then).
 */
public record GisSnapshotAnalyzedEvent(
    UUID projectId,
    UUID snapshotId
) {
}
