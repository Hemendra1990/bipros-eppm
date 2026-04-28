package com.bipros.analytics.etl.watermark;

import com.bipros.analytics.etl.SyncReport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
public class ClickHouseWatermarkStore implements WatermarkStore {

    private static final String SELECT_SQL =
            "SELECT table_name, last_synced_at, last_run_at, last_run_status, last_run_message " +
            "FROM etl_watermarks FINAL WHERE table_name = ?";

    private static final String UPSERT_SQL =
            "INSERT INTO etl_watermarks " +
            "(table_name, last_synced_at, last_run_at, rows_pulled, last_run_status, last_run_message, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate ch;

    public ClickHouseWatermarkStore(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate ch) {
        this.ch = ch;
    }

    @Override
    public Watermark read(String tableName) {
        List<Watermark> rows = ch.query(SELECT_SQL, (rs, i) -> new Watermark(
                rs.getString("table_name"),
                toInstant(rs.getTimestamp("last_synced_at")),
                toInstant(rs.getTimestamp("last_run_at")),
                rs.getString("last_run_status"),
                rs.getString("last_run_message")
        ), tableName);
        return rows.isEmpty() ? Watermark.seed(tableName) : rows.get(0);
    }

    @Override
    public void writeSuccess(String tableName, SyncReport report) {
        Watermark prior = read(tableName);
        Instant newSynced = report.newWatermark() != null ? report.newWatermark() : prior.lastSyncedAt();
        Instant runAt = Instant.now();
        ch.update(UPSERT_SQL,
                tableName,
                Timestamp.from(newSynced != null ? newSynced : Instant.EPOCH),
                Timestamp.from(runAt),
                report.rowsPulled(),
                "SUCCESS",
                report.message(),
                Timestamp.from(runAt));
    }

    @Override
    public void writeFailure(String tableName, String message) {
        Watermark prior = read(tableName);
        Instant runAt = Instant.now();
        ch.update(UPSERT_SQL,
                tableName,
                Timestamp.from(prior.lastSyncedAt() != null ? prior.lastSyncedAt() : Instant.EPOCH),
                Timestamp.from(runAt),
                0L,
                "FAILED",
                truncate(message, 500),
                Timestamp.from(runAt));
    }

    @Override
    public void reset(String tableName) {
        Instant runAt = Instant.now();
        ch.update(UPSERT_SQL,
                tableName,
                Timestamp.from(Instant.EPOCH),
                Timestamp.from(runAt),
                0L,
                "SUCCESS",
                "reset for backfill",
                Timestamp.from(runAt));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
