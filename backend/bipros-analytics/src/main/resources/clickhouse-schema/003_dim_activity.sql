CREATE TABLE IF NOT EXISTS dim_activity (
    id                       String,
    code                     String,
    name                     String,
    description              Nullable(String),
    project_id               String,
    wbs_node_id              String,
    activity_type            LowCardinality(String),
    duration_type            LowCardinality(Nullable(String)),
    percent_complete_type    LowCardinality(Nullable(String)),
    status                   LowCardinality(String),
    calendar_id              Nullable(String),
    is_critical              UInt8,
    sort_order               Nullable(Int32),
    chainage_from_m          Nullable(Int64),
    chainage_to_m            Nullable(Int64),
    assigned_to              Nullable(String),
    responsible_user_id      Nullable(String),
    primary_constraint_type  LowCardinality(Nullable(String)),
    primary_constraint_date  Nullable(Date),
    secondary_constraint_type LowCardinality(Nullable(String)),
    secondary_constraint_date Nullable(Date),
    created_at               DateTime64(3),
    updated_at               DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY id
SETTINGS index_granularity = 8192;
