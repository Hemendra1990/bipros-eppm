CREATE TABLE IF NOT EXISTS fact_resource_assignments (
    id                   String,
    activity_id          String,
    resource_id          String,
    project_id           String,
    role_id              Nullable(String),
    resource_curve_id    Nullable(String),
    planned_units        Nullable(Float64),
    actual_units         Nullable(Float64),
    remaining_units      Nullable(Float64),
    at_completion_units  Nullable(Float64),
    planned_cost         Nullable(Decimal(19,4)),
    actual_cost          Nullable(Decimal(19,4)),
    remaining_cost       Nullable(Decimal(19,4)),
    at_completion_cost   Nullable(Decimal(19,4)),
    rate_type            Nullable(String),
    planned_start_date   Nullable(Date),
    planned_finish_date  Nullable(Date),
    actual_start_date    Nullable(Date),
    actual_finish_date   Nullable(Date),
    created_at           DateTime64(3),
    updated_at           DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY id
SETTINGS index_granularity = 8192;
