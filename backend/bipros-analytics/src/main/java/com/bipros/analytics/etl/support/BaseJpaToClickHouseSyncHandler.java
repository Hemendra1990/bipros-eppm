package com.bipros.analytics.etl.support;

import com.bipros.analytics.etl.SyncHandler;
import com.bipros.analytics.etl.SyncReport;
import com.bipros.common.model.BaseEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable base for the common shape: pull rows from a single JPA source whose
 * {@link BaseEntity#getUpdatedAt()} is the watermark, and bulk-insert them into a ClickHouse
 * table via {@link JdbcTemplate#batchUpdate(String, List)}.
 *
 * <p>Subclasses supply the source-side {@code Page<E> fetchPage(...)}, the row→args mapper, and
 * the parameterised insert SQL. Default {@link #cadence()} is 10 minutes; override for
 * snapshot-style handlers.
 *
 * <p>Pages are fetched ascending by {@code (updatedAt, id)} so the watermark advances
 * monotonically and a crash-then-restart re-pulls only the unfinished tail. Page size defaults
 * to 5 000 rows — large enough to amortise round-trips, small enough to keep memory bounded.
 */
@Slf4j
public abstract class BaseJpaToClickHouseSyncHandler<E extends BaseEntity> implements SyncHandler {

    public static final int DEFAULT_PAGE_SIZE = 5_000;

    protected final JdbcTemplate clickhouse;

    protected BaseJpaToClickHouseSyncHandler(JdbcTemplate clickhouse) {
        this.clickhouse = clickhouse;
    }

    @Override
    public Duration cadence() {
        return Duration.ofMinutes(10);
    }

    /** Spring Data page query for rows whose {@code updated_at &gt; since}. */
    protected abstract Page<E> fetchPage(Instant since, Pageable pageable);

    /** Maps one source entity to the positional argument array for {@link #insertSql()}. */
    protected abstract Object[] mapRow(E entity);

    /** Parameterised {@code INSERT INTO ... VALUES (?, ?, ...)} statement. */
    protected abstract String insertSql();

    protected int pageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    /**
     * Must NOT be {@code final}. Spring uses CGLIB to proxy this class because of
     * {@code @Transactional}. CGLIB proxies are instantiated via Objenesis without invoking
     * the user constructor, so the proxy's {@code clickhouse} field is null. CGLIB hides that
     * by overriding each method to route through to the target instance (whose constructor DID
     * run); but {@code final} methods cannot be overridden, so {@code proxy.sync()} would run
     * on the proxy itself with a null field → NPE on every page-flush. Keeping this method
     * non-final lets the proxy delegate to the target correctly.
     */
    @Override
    @Transactional(readOnly = true)
    public SyncReport sync(Instant since) {
        Instant currentWm = since == null ? Instant.EPOCH : since;
        Instant newWm = currentWm;
        long pulled = 0;
        long written = 0;
        int pageNum = 0;
        Pageable pageable = PageRequest.of(pageNum, pageSize(),
                Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id")));

        while (true) {
            Page<E> page = fetchPage(currentWm, pageable);
            if (page.isEmpty()) break;
            List<Object[]> batch = new ArrayList<>(page.getNumberOfElements());
            E lastRow = null;
            for (E e : page.getContent()) {
                batch.add(mapRow(e));
                lastRow = e;
            }
            int[] rc = clickhouse.batchUpdate(insertSql(), batch);
            for (int n : rc) written += Math.max(n, 0);
            pulled += page.getNumberOfElements();
            if (lastRow != null && lastRow.getUpdatedAt() != null) {
                newWm = lastRow.getUpdatedAt();
            }
            if (!page.hasNext()) break;
            pageNum++;
            pageable = PageRequest.of(pageNum, pageSize(),
                    Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id")));
        }

        return new SyncReport(newWm, pulled, written, null);
    }
}
