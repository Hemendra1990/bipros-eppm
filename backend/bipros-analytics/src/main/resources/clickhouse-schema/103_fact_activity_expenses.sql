CREATE TABLE IF NOT EXISTS fact_activity_expenses (
    id                    String,
    activity_id           Nullable(String),
    project_id            String,
    cost_account_id       Nullable(String),
    name                  Nullable(String),
    description           Nullable(String),
    expense_category      Nullable(String),
    budgeted_cost         Nullable(Decimal(19,2)),
    actual_cost           Nullable(Decimal(19,2)),
    remaining_cost        Nullable(Decimal(19,2)),
    at_completion_cost    Nullable(Decimal(19,2)),
    percent_complete      Nullable(Float64),
    planned_start_date    Nullable(Date),
    planned_finish_date   Nullable(Date),
    actual_start_date     Nullable(Date),
    actual_finish_date    Nullable(Date),
    currency              Nullable(String),
    created_at            DateTime64(3),
    updated_at            DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY id
SETTINGS index_granularity = 8192;
