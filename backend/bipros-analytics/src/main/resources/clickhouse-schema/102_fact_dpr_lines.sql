CREATE TABLE IF NOT EXISTS fact_dpr_lines (
    id                  String,
    project_id          String,
    report_date         Date,
    supervisor_name     String,
    chainage_from_m     Nullable(Int64),
    chainage_to_m       Nullable(Int64),
    activity_name       String,
    wbs_node_id         Nullable(String),
    boq_item_no         Nullable(String),
    unit                String,
    qty_executed        Decimal(18,3),
    cumulative_qty      Nullable(Decimal(18,3)),
    weather_condition   Nullable(String),
    remarks             Nullable(String),
    created_at          DateTime64(3),
    updated_at          DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(report_date)
ORDER BY id
SETTINGS index_granularity = 8192;
