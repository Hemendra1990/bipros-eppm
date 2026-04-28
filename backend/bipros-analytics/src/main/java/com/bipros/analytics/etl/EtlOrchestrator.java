package com.bipros.analytics.etl;

import com.bipros.analytics.etl.watermark.Watermark;
import com.bipros.analytics.etl.watermark.WatermarkStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Iterates every {@link SyncHandler} bean every 10 minutes (configurable). Each handler decides
 * its own cadence; the orchestrator skips a handler that's not yet due. Failures of one handler
 * are logged and recorded on the watermark row but never block other handlers in the same tick.
 *
 * <p>Per-table {@link EtlMutex} prevents collisions with the synchronous backfill endpoint —
 * if a backfill is in progress for table T, the orchestrator's tick for T is skipped this round
 * and resumes on the next.
 */
@Component
@ConditionalOnProperty(name = "bipros.analytics.etl.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EtlProperties.class)
@Slf4j
public class EtlOrchestrator {

    private final List<SyncHandler> handlers;
    private final WatermarkStore watermarks;
    private final EtlMutex mutex;

    public EtlOrchestrator(List<SyncHandler> handlers, WatermarkStore watermarks, EtlMutex mutex) {
        this.handlers = handlers;
        this.watermarks = watermarks;
        this.mutex = mutex;
        log.info("EtlOrchestrator initialised with {} handlers: {}",
                handlers.size(),
                handlers.stream().map(SyncHandler::tableName).toList());
    }

    @Scheduled(cron = "${bipros.analytics.etl.schedule-cron:0 */10 * * * *}")
    public void runAll() {
        Instant tickStart = Instant.now();
        for (SyncHandler h : handlers) {
            if (!isDue(h, tickStart)) continue;
            ReentrantLock lock = mutex.tryAcquire(h.tableName());
            if (lock == null) {
                log.info("ETL skip {} — locked by backfill", h.tableName());
                continue;
            }
            try {
                Watermark wm = watermarks.read(h.tableName());
                Instant since = wm.lastSyncedAt() == null ? Instant.EPOCH : wm.lastSyncedAt();
                SyncReport rep = h.sync(since);
                watermarks.writeSuccess(h.tableName(), rep);
                log.info("ETL {} synced rows={} written={} newWatermark={}",
                        h.tableName(), rep.rowsPulled(), rep.rowsWritten(), rep.newWatermark());
            } catch (Exception ex) {
                log.error("ETL {} FAILED", h.tableName(), ex);
                watermarks.writeFailure(h.tableName(), ex.getMessage());
            } finally {
                lock.unlock();
            }
        }
    }

    private boolean isDue(SyncHandler h, Instant now) {
        Watermark wm = watermarks.read(h.tableName());
        if (wm.lastRunAt() == null) return true;
        Duration elapsed = Duration.between(wm.lastRunAt(), now);
        return elapsed.compareTo(h.cadence()) >= 0;
    }
}
