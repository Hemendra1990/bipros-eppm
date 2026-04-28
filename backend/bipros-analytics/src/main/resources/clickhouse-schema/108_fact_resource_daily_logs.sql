CREATE TABLE IF NOT EXISTS fact_resource_daily_logs (
    id                   String,
    resource_id          String,
    log_date             Date,
    planned_units        Nullable(Float64),
    actual_units         Nullable(Float64),
    utilisation_percent  Nullable(Float64),
    wbs_package_code     Nullable(String),
    remarks              Nullable(String),
    created_at           DateTime64(3),
    updated_at           DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(log_date)
ORDER BY (resource_id, log_date)
SETTINGS index_granularity = 8192;
