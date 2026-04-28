CREATE TABLE IF NOT EXISTS fact_activity_progress (
    activity_id                String,
    snapshot_date              Date,
    project_id                 String,
    wbs_node_id                String,
    status                     LowCardinality(String),
    percent_complete           Nullable(Float64),
    physical_percent_complete  Nullable(Float64),
    duration_percent_complete  Nullable(Float64),
    units_percent_complete     Nullable(Float64),
    planned_start_date         Nullable(Date),
    planned_finish_date        Nullable(Date),
    early_start_date           Nullable(Date),
    early_finish_date          Nullable(Date),
    late_start_date            Nullable(Date),
    late_finish_date           Nullable(Date),
    actual_start_date          Nullable(Date),
    actual_finish_date         Nullable(Date),
    original_duration          Nullable(Float64),
    remaining_duration         Nullable(Float64),
    at_completion_duration     Nullable(Float64),
    total_float                Nullable(Float64),
    free_float                 Nullable(Float64),
    is_critical                UInt8,
    suspend_date               Nullable(Date),
    resume_date                Nullable(Date),
    assigned_to                Nullable(String),
    responsible_user_id        Nullable(String),
    updated_at                 DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(snapshot_date)
ORDER BY (activity_id, snapshot_date)
SETTINGS index_granularity = 8192;
