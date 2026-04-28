package com.bipros.analytics.application.service;

import com.bipros.analytics.application.dto.BackfillResponse;
import com.bipros.analytics.etl.EtlMutex;
import com.bipros.analytics.etl.SyncHandler;
import com.bipros.analytics.etl.SyncReport;
import com.bipros.analytics.etl.watermark.WatermarkStore;
import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronous single-table backfill orchestrator. Chosen flow:
 * <ol>
 *   <li>Look up the {@link SyncHandler} matching the requested table; reject unknowns.</li>
 *   <li>Acquire the per-table {@link EtlMutex} (blocking) so the orchestrator's tick skips
 *       this table while we work.</li>
 *   <li>Count rows before the truncate.</li>
 *   <li>{@code TRUNCATE TABLE} the ClickHouse target.</li>
 *   <li>Reset the watermark to epoch.</li>
 *   <li>Invoke {@code handler.sync(EPOCH)} to re-pull every source row.</li>
 *   <li>Count rows after; persist the new watermark.</li>
 * </ol>
 *
 * <p>Sync execution is fine for Phase 1's dataset sizes (≤ 100K rows / table, completes in
 * seconds). When backfills regularly exceed ~60s, switch to async + job-id polling.
 */
@Service
@Slf4j
public class BackfillService {

    private final Map<String, SyncHandler> registry;
    private final EtlMutex mutex;
    private final WatermarkStore watermarks;
    private final JdbcTemplate ch;

    public BackfillService(List<SyncHandler> handlers,
                           EtlMutex mutex,
                           WatermarkStore watermarks,
                           @Qualifier("clickhouseJdbcTemplate") JdbcTemplate ch) {
        this.registry = new HashMap<>();
        for (SyncHandler h : handlers) {
            this.registry.put(h.tableName(), h);
        }
        this.mutex = mutex;
        this.watermarks = watermarks;
        this.ch = ch;
    }

    public BackfillResponse backfill(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new BusinessRuleException("UNKNOWN_TABLE", "table parameter is required");
        }
        SyncHandler handler = registry.get(tableName);
        if (handler == null) {
            throw new BusinessRuleException("UNKNOWN_TABLE",
                    "Unknown ETL table: " + tableName + ". Known: " + registry.keySet());
        }

        Instant startedAt = Instant.now();
        ReentrantLock lock = mutex.acquire(tableName);
        try {
            long rowsBefore = countRows(tableName);
            ch.execute("TRUNCATE TABLE " + tableName);
            watermarks.reset(tableName);

            log.info("Backfill {} starting (rowsBefore={})", tableName, rowsBefore);
            SyncReport rep = handler.sync(Instant.EPOCH);
            watermarks.writeSuccess(tableName, rep);

            long rowsAfter = countRows(tableName);
            Instant finishedAt = Instant.now();
            long ms = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
            String message = "rowsPulled=" + rep.rowsPulled() + " rowsWritten=" + rep.rowsWritten();
            log.info("Backfill {} complete in {}ms (rowsBefore={} rowsAfter={})",
                    tableName, ms, rowsBefore, rowsAfter);
            return new BackfillResponse(tableName, rowsBefore, rowsAfter,
                    startedAt, finishedAt, ms, "SUCCESS", message);
        } catch (RuntimeException ex) {
            log.error("Backfill {} FAILED", tableName, ex);
            watermarks.writeFailure(tableName, ex.getMessage());
            Instant finishedAt = Instant.now();
            long ms = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
            return new BackfillResponse(tableName, 0, 0, startedAt, finishedAt, ms, "FAILED",
                    ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private long countRows(String tableName) {
        Long n = ch.queryForObject("SELECT count() FROM " + tableName + " FINAL", Long.class);
        return n == null ? 0L : n;
    }
}
