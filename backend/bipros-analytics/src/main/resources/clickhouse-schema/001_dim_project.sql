CREATE TABLE IF NOT EXISTS dim_project (
    id                   String,
    code                 String,
    name                 String,
    description          Nullable(String),
    eps_node_id          Nullable(String),
    obs_node_id          Nullable(String),
    status               LowCardinality(String),
    planned_start_date   Nullable(Date),
    planned_finish_date  Nullable(Date),
    data_date            Nullable(Date),
    must_finish_by_date  Nullable(Date),
    priority             Int32,
    category             Nullable(String),
    morth_code           Nullable(String),
    from_chainage_m      Nullable(Int64),
    to_chainage_m        Nullable(Int64),
    from_location        Nullable(String),
    to_location          Nullable(String),
    total_length_km      Nullable(Decimal(10,3)),
    active_baseline_id   Nullable(String),
    owner_id             Nullable(String),
    archived_at          Nullable(DateTime64(3)),
    archived_by          Nullable(String),
    created_at           DateTime64(3),
    updated_at           DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY id
SETTINGS index_granularity = 8192;
