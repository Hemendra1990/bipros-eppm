CREATE TABLE IF NOT EXISTS fact_labour_returns (
    id                String,
    project_id        String,
    contractor_name   String,
    return_date       Date,
    skill_category    LowCardinality(String),
    head_count        Int32,
    man_days          Float64,
    wbs_node_id       Nullable(String),
    site_location     Nullable(String),
    remarks           Nullable(String),
    created_at        DateTime64(3),
    updated_at        DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(return_date)
ORDER BY id
SETTINGS index_granularity = 8192;
