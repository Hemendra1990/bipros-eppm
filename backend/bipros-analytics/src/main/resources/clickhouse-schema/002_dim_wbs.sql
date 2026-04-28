CREATE TABLE IF NOT EXISTS dim_wbs (
    id                          String,
    code                        String,
    name                        String,
    project_id                  String,
    parent_id                   Nullable(String),
    obs_node_id                 Nullable(String),
    sort_order                  Int32,
    wbs_level                   Nullable(Int32),
    wbs_type                    LowCardinality(Nullable(String)),
    phase                       LowCardinality(Nullable(String)),
    wbs_status                  LowCardinality(Nullable(String)),
    asset_class                 LowCardinality(Nullable(String)),
    responsible_organisation_id Nullable(String),
    planned_start               Nullable(Date),
    planned_finish              Nullable(Date),
    budget_crores               Nullable(Decimal(14,2)),
    gis_polygon_id              Nullable(String),
    chainage_from_m             Nullable(Int64),
    chainage_to_m               Nullable(Int64),
    summary_duration            Nullable(Float64),
    summary_percent_complete    Nullable(Float64),
    created_at                  DateTime64(3),
    updated_at                  DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY id
SETTINGS index_granularity = 8192;
