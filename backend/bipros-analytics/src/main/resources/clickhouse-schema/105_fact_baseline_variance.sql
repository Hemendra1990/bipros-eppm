CREATE TABLE IF NOT EXISTS fact_baseline_variance (
    activity_id              String,
    baseline_id              String,
    snapshot_date            Date,
    project_id               String,
    baseline_early_start     Nullable(Date),
    baseline_early_finish    Nullable(Date),
    baseline_late_start      Nullable(Date),
    baseline_late_finish     Nullable(Date),
    baseline_planned_cost    Nullable(Decimal(19,2)),
    baseline_percent_complete Nullable(Float64),
    current_planned_start    Nullable(Date),
    current_planned_finish   Nullable(Date),
    current_actual_start     Nullable(Date),
    current_actual_finish    Nullable(Date),
    current_percent_complete Nullable(Float64),
    start_variance_days      Nullable(Int32),
    finish_variance_days     Nullable(Int32),
    duration_variance        Nullable(Float64),
    percent_complete_variance Nullable(Float64),
    updated_at               DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(snapshot_date)
ORDER BY (activity_id, baseline_id, snapshot_date)
SETTINGS index_granularity = 8192;
