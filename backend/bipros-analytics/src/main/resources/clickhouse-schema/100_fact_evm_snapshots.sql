CREATE TABLE IF NOT EXISTS fact_evm_snapshots (
    id                            String,
    project_id                    String,
    wbs_node_id                   String,
    activity_id                   String,
    financial_period_id           Nullable(String),
    data_date                     Date,
    budget_at_completion          Nullable(Decimal(19,2)),
    planned_value                 Nullable(Decimal(19,2)),
    earned_value                  Nullable(Decimal(19,2)),
    actual_cost                   Nullable(Decimal(19,2)),
    schedule_variance             Nullable(Decimal(19,2)),
    cost_variance                 Nullable(Decimal(19,2)),
    schedule_performance_index    Nullable(Float64),
    cost_performance_index        Nullable(Float64),
    to_complete_performance_index Nullable(Float64),
    estimate_at_completion        Nullable(Decimal(19,2)),
    estimate_to_complete          Nullable(Decimal(19,2)),
    variance_at_completion        Nullable(Decimal(19,2)),
    performance_percent_complete  Nullable(Float64),
    evm_technique                 LowCardinality(Nullable(String)),
    etc_method                    LowCardinality(Nullable(String)),
    created_at                    DateTime64(3),
    updated_at                    DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(data_date)
ORDER BY (project_id, wbs_node_id, activity_id, data_date)
SETTINGS index_granularity = 8192;
