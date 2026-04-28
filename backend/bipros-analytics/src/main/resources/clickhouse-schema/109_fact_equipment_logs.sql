CREATE TABLE IF NOT EXISTS fact_equipment_logs (
    id                String,
    resource_id       String,
    project_id        String,
    log_date          Date,
    deployment_site   Nullable(String),
    operating_hours   Nullable(Float64),
    idle_hours        Nullable(Float64),
    breakdown_hours   Nullable(Float64),
    fuel_consumed     Nullable(Float64),
    operator_name     Nullable(String),
    status            LowCardinality(String),
    remarks           Nullable(String),
    created_at        DateTime64(3),
    updated_at        DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(log_date)
ORDER BY id
SETTINGS index_granularity = 8192;
