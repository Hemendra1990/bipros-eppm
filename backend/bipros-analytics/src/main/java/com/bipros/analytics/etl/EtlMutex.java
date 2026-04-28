package com.bipros.analytics.etl;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-table {@link ReentrantLock} registry. Backfills acquire the lock by blocking; the 10-min
 * orchestrator tick uses {@link #tryAcquire(String)} so it skips a table while a backfill is
 * running and resumes on the next tick.
 *
 * <p>Single-replica only — Phase 1 explicitly does not coordinate across JVMs. If the ETL
 * eventually needs to scale horizontally, swap this for a Postgres advisory lock or a Redis
 * lease without changing the call sites.
 */
@Component
public class EtlMutex {

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock acquire(String table) {
        ReentrantLock lock = locks.computeIfAbsent(table, k -> new ReentrantLock());
        lock.lock();
        return lock;
    }

    /**
     * Returns the held lock if acquisition succeeded, or {@code null} when another thread holds
     * it. Callers must {@code unlock()} the returned lock once their critical section ends.
     */
    public ReentrantLock tryAcquire(String table) {
        ReentrantLock lock = locks.computeIfAbsent(table, k -> new ReentrantLock());
        return lock.tryLock() ? lock : null;
    }
}
