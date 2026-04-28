CREATE TABLE IF NOT EXISTS etl_watermarks (
    table_name        String,
    last_synced_at    DateTime64(3),
    last_run_at       DateTime64(3),
    rows_pulled       UInt64,
    last_run_status   Enum8('SUCCESS' = 1, 'FAILED' = 2, 'PARTIAL' = 3),
    last_run_message  Nullable(String),
    updated_at        DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY table_name
SETTINGS index_granularity = 8192;
