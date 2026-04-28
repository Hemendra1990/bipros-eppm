package com.bipros.analytics.etl.support;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tiny helpers used by every {@code etl/handler/*} class to convert Java types to the JDBC
 * positional args ClickHouse expects. Keeps handlers free of repetitive null/UUID gymnastics.
 */
public final class HandlerSupport {

    private HandlerSupport() {
    }

    /** UUID → String, never null (empty string for missing values). Used for ORDER BY columns. */
    public static String uuidOrEmpty(UUID id) {
        return id == null ? "" : id.toString();
    }

    /** UUID → String or null (used for plain Nullable(String) columns). */
    public static String uuidOrNull(UUID id) {
        return id == null ? null : id.toString();
    }

    public static Date toSqlDate(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    public static Timestamp toSqlTs(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    public static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    public static BigDecimal nullable(BigDecimal v) {
        return v;
    }

    /** Non-null fallback for primitive booleans serialised as ClickHouse UInt8. */
    public static int boolToInt(Boolean b) {
        return b != null && b ? 1 : 0;
    }
}
