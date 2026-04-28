package com.bipros.analytics.etl;

import java.time.Duration;
import java.time.Instant;

/**
 * One handler per ClickHouse fact/dim table. Implementations are Spring beans collected by
 * {@link EtlOrchestrator}; the {@link #tableName()} value is also the key used in
 * {@code etl_watermarks} and the registry consulted by the backfill endpoint.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #cadence()} is how often the table should be synced (e.g. 10 minutes for most
 *       tables, 1 day for snapshot tables). The orchestrator skips a handler whose last run
 *       was less than {@code cadence()} ago.</li>
 *   <li>{@link #sync(Instant)} pulls source rows whose {@code updated_at &gt; since}, writes
 *       them to ClickHouse, and returns the new high-water mark in {@link SyncReport}. The
 *       orchestrator owns reading and writing the watermark row.</li>
 *   <li>Implementations must be idempotent: re-running with the same {@code since} must not
 *       cause duplicate or missing rows in ClickHouse (achieved via
 *       {@code ReplacingMergeTree(updated_at)}).</li>
 * </ul>
 */
public interface SyncHandler {

    String tableName();

    Duration cadence();

    SyncReport sync(Instant since);
}
