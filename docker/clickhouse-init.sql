-- ClickHouse bootstrap: create database if not exists (the docker env CLICKHOUSE_DB already does this,
-- but this script is idempotent and can be rerun manually).
CREATE DATABASE IF NOT EXISTS bipros_analytics;

-- Dimension tables (small, refreshed nightly)
CREATE TABLE IF NOT EXISTS bipros_analytics.dim_project (
    project_id UUID,
    code String,
    name String,
    status LowCardinality(String),
    portfolio_id Nullable(UUID),
    org_id Nullable(UUID),
    start_date Date,
    finish_date Date,
    currency LowCardinality(String),
    obs_node_id Nullable(UUID),
    updated_at DateTime,
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY project_id;

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_wbs (
    wbs_id UUID,
    project_id UUID,
    parent_wbs_id Nullable(UUID),
    code String,
    name String,
    level UInt8,
    weight Float64,
    path String,
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY (project_id, wbs_id);

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_activity (
    activity_id UUID,
    project_id UUID,
    wbs_id UUID,
    code String,
    name String,
    activity_type LowCardinality(String),
    uom LowCardinality(String),
    bq_quantity Float64,
    planned_start Date,
    planned_finish Date,
    chainage_from_m Nullable(Float64),
    chainage_to_m Nullable(Float64),
    is_critical UInt8,
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY (project_id, activity_id);

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_resource (
    resource_id UUID,
    project_id Nullable(UUID),
    resource_type LowCardinality(String),
    code String,
    name String,
    uom LowCardinality(String),
    unit_rate Decimal(18,4),
    is_subcontractor UInt8,
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY (resource_type, resource_id);

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_cost_account (
    cost_account_id UUID,
    project_id UUID,
    code String,
    name String,
    parent_id Nullable(UUID),
    category LowCardinality(String),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY (project_id, cost_account_id);

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_calendar (
    date Date,
    year UInt16,
    quarter UInt8,
    month UInt8,
    week UInt8,
    iso_week UInt8,
    day_of_week UInt8,
    is_business_day UInt8,
    fiscal_period UInt8
) ENGINE = MergeTree
  ORDER BY date;

-- Calendar seed: backfill 10 years from 2020-01-01 to 2029-12-31
-- ClickHouse does not support INSERT ... SELECT from generate_series directly in the same way as Postgres.
-- We rely on the Java application to seed the calendar table on first boot, or a one-time insert via VALUES.
-- For now, the ETL job will handle calendar seeding.

-- ========================================================================
-- Fact tables
-- ========================================================================

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_activity_progress_daily (
    project_id UUID,
    activity_id UUID,
    date Date,
    pct_complete_physical Float32,
    pct_complete_duration Float32,
    qty_executed Float64,
    cumulative_qty Float64,
    chainage_from_m Nullable(Float64),
    chainage_to_m Nullable(Float64),
    source LowCardinality(String),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(date)
  ORDER BY (project_id, activity_id, date)
  TTL date + INTERVAL 7 YEAR;

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_resource_usage_daily (
    project_id UUID,
    activity_id UUID,
    resource_id UUID,
    resource_type LowCardinality(String),
    date Date,
    hours_worked Float32,
    days_worked Float32,
    qty_executed Float64,
    productivity_actual Float32,
    productivity_norm Float32,
    cost Decimal(18,4),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(date)
  ORDER BY (project_id, activity_id, resource_id, date)
  TTL date + INTERVAL 7 YEAR;

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_cost_daily (
    project_id UUID,
    wbs_id UUID,
    activity_id UUID,
    date Date,
    cost_account_id UUID,
    labor_cost Decimal(18,4),
    material_cost Decimal(18,4),
    equipment_cost Decimal(18,4),
    expense_cost Decimal(18,4),
    total_actual Decimal(18,4),
    total_planned Decimal(18,4),
    total_earned Decimal(18,4),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(date)
  ORDER BY (project_id, wbs_id, activity_id, cost_account_id, date)
  TTL date + INTERVAL 7 YEAR;

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_evm_daily (
    project_id UUID,
    wbs_id Nullable(UUID),
    activity_id Nullable(UUID),
    date Date,
    bac Decimal(18,4),
    pv Decimal(18,4),
    ev Decimal(18,4),
    ac Decimal(18,4),
    cv Decimal(18,4),
    sv Decimal(18,4),
    cpi Float64,
    spi Float64,
    tcpi Float64,
    eac Decimal(18,4),
    etc_cost Decimal(18,4),
    vac Decimal(18,4),
    period_source LowCardinality(String),
    interpolation LowCardinality(String),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(date)
  ORDER BY (project_id,
            coalesce(wbs_id, toUUID('00000000-0000-0000-0000-000000000000')),
            coalesce(activity_id, toUUID('00000000-0000-0000-0000-000000000000')),
            date)
  TTL date + INTERVAL 7 YEAR;

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_dpr_logs (
    project_id UUID,
    activity_id UUID,
    dpr_id UUID,
    report_date Date,
    supervisor_user_id UUID,
    supervisor_name String,
    chainage_from_m Nullable(Float64),
    chainage_to_m Nullable(Float64),
    qty_executed Float64,
    cumulative_qty Float64,
    weather LowCardinality(String),
    temperature_c Nullable(Float32),
    remarks_text String,
    remarks_embedding Array(Float32),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(report_date)
  ORDER BY (project_id, activity_id, report_date, dpr_id)
  TTL report_date + INTERVAL 7 YEAR;

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_risk_snapshot_daily (
    project_id UUID,
    risk_id UUID,
    date Date,
    probability Float32,
    impact_cost Decimal(18,4),
    impact_days Int32,
    rag LowCardinality(String),
    status LowCardinality(String),
    monte_carlo_p50 Decimal(18,4),
    monte_carlo_p80 Decimal(18,4),
    monte_carlo_p95 Decimal(18,4),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(date)
  ORDER BY (project_id, risk_id, date)
  TTL date + INTERVAL 5 YEAR;

-- Phase: extend risk snapshot with full P6 fields. Idempotent ALTERs.
ALTER TABLE bipros_analytics.fact_risk_snapshot_daily
    ADD COLUMN IF NOT EXISTS risk_score Nullable(Float64),
    ADD COLUMN IF NOT EXISTS residual_risk_score Nullable(Float64),
    ADD COLUMN IF NOT EXISTS risk_type LowCardinality(String) DEFAULT 'THREAT',
    ADD COLUMN IF NOT EXISTS owner_id Nullable(UUID),
    ADD COLUMN IF NOT EXISTS category_id Nullable(UUID),
    ADD COLUMN IF NOT EXISTS post_response_probability Nullable(Float32),
    ADD COLUMN IF NOT EXISTS post_response_impact_cost Nullable(Int32),
    ADD COLUMN IF NOT EXISTS post_response_impact_schedule Nullable(Int32),
    ADD COLUMN IF NOT EXISTS pre_response_exposure_cost Nullable(Decimal(18,4)),
    ADD COLUMN IF NOT EXISTS post_response_exposure_cost Nullable(Decimal(18,4)),
    ADD COLUMN IF NOT EXISTS exposure_start_date Nullable(Date),
    ADD COLUMN IF NOT EXISTS exposure_finish_date Nullable(Date),
    ADD COLUMN IF NOT EXISTS response_type LowCardinality(String) DEFAULT '',
    ADD COLUMN IF NOT EXISTS trend LowCardinality(String) DEFAULT '',
    ADD COLUMN IF NOT EXISTS identified_date Nullable(Date),
    ADD COLUMN IF NOT EXISTS identified_by_id Nullable(UUID);

-- ========================================================================
-- Risk dimension
-- ========================================================================

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_risk (
    risk_id UUID,
    project_id UUID,
    code String,
    title String,
    risk_type LowCardinality(String),
    category_id Nullable(UUID),
    category_name String,
    owner_id Nullable(UUID),
    owner_name String,
    status LowCardinality(String),
    rag LowCardinality(String),
    trend LowCardinality(String),
    response_type LowCardinality(String),
    identified_date Nullable(Date),
    identified_by_id Nullable(UUID),
    closed_date Nullable(Date),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY (project_id, risk_id);

-- ========================================================================
-- Permit dimensions and lifecycle fact
-- ========================================================================

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_permit_type (
    permit_type_template_id UUID,
    code String,
    name String,
    color_hex String,
    icon_key String,
    max_duration_hours Int32,
    requires_gas_test UInt8,
    requires_isolation UInt8,
    jsa_required UInt8,
    blasting_required UInt8,
    diving_required UInt8,
    default_risk_level LowCardinality(String),
    night_work_policy LowCardinality(String),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY permit_type_template_id;

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_permit (
    permit_id UUID,
    project_id UUID,
    permit_code String,
    permit_type_template_id UUID,
    parent_permit_id Nullable(UUID),
    status LowCardinality(String),
    risk_level LowCardinality(String),
    shift LowCardinality(String),
    contractor_org_id Nullable(UUID),
    location_zone String,
    chainage_marker String,
    supervisor_name String,
    start_at DateTime64(3),
    end_at DateTime64(3),
    valid_from Nullable(DateTime64(3)),
    valid_to Nullable(DateTime64(3)),
    declaration_accepted_at Nullable(DateTime64(3)),
    closed_at Nullable(DateTime64(3)),
    closed_by Nullable(UUID),
    revoked_at Nullable(DateTime64(3)),
    revoked_by Nullable(UUID),
    expired_at Nullable(DateTime64(3)),
    suspended_at Nullable(DateTime64(3)),
    total_approvals_required Int32,
    approvals_completed Int32,
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY (project_id, permit_id);

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_permit_lifecycle (
    project_id UUID,
    permit_id UUID,
    permit_type_template_id UUID,
    event_type LowCardinality(String),
    occurred_at DateTime64(3),
    occurred_date Date MATERIALIZED toDate(occurred_at),
    actor_user_id Nullable(UUID),
    risk_level LowCardinality(String),
    permit_status LowCardinality(String),
    payload_json String,
    duration_hours_to_event Nullable(Float32),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(occurred_at)
  ORDER BY (project_id, permit_id, occurred_at, event_type)
  TTL toDate(occurred_at) + INTERVAL 7 YEAR;

-- ========================================================================
-- Labour dimension and daily fact
-- ========================================================================

CREATE TABLE IF NOT EXISTS bipros_analytics.dim_labour_designation (
    designation_id UUID,
    code String,
    designation String,
    category LowCardinality(String),
    trade String,
    grade LowCardinality(String),
    nationality LowCardinality(String),
    experience_years_min Int16,
    default_daily_rate Decimal(18,4),
    skills Array(String),
    certifications Array(String),
    status LowCardinality(String),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  ORDER BY designation_id;

CREATE TABLE IF NOT EXISTS bipros_analytics.fact_labour_daily (
    project_id UUID,
    labour_return_id Nullable(UUID),
    deployment_id Nullable(UUID),
    designation_id Nullable(UUID),
    skill_category LowCardinality(String),
    contractor_name String,
    contractor_org_id Nullable(UUID),
    wbs_id Nullable(UUID),
    site_location String,
    date Date,
    head_count Int32,
    man_days Float32,
    planned_head_count Nullable(Int32),
    daily_rate Nullable(Decimal(18,4)),
    daily_cost Nullable(Decimal(18,4)),
    source LowCardinality(String),
    event_ts DateTime64(3),
    _version UInt64
) ENGINE = ReplacingMergeTree(_version)
  PARTITION BY toYYYYMM(date)
  ORDER BY (project_id, date, contractor_name, skill_category,
            coalesce(designation_id, toUUID('00000000-0000-0000-0000-000000000000')))
  TTL date + INTERVAL 7 YEAR;

-- ========================================================================
-- Materialized Views
-- ========================================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS bipros_analytics.mv_project_kpi_daily
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(date) ORDER BY (project_id, date)
AS SELECT project_id, date,
       sum(total_actual) AS ac, sum(total_planned) AS pv, sum(total_earned) AS ev,
       count() AS rows
FROM bipros_analytics.fact_cost_daily GROUP BY project_id, date;

CREATE MATERIALIZED VIEW IF NOT EXISTS bipros_analytics.mv_portfolio_scurve_weekly
ENGINE = AggregatingMergeTree
PARTITION BY toYYYYMM(week_start)
ORDER BY (portfolio_id, week_start)
AS SELECT assumeNotNull(p.portfolio_id) AS portfolio_id, toMonday(e.date) AS week_start,
          sumState(e.pv) AS pv_state, sumState(e.ev) AS ev_state, sumState(e.ac) AS ac_state
FROM bipros_analytics.fact_evm_daily e INNER JOIN bipros_analytics.dim_project p ON e.project_id = p.project_id
WHERE p.portfolio_id IS NOT NULL
GROUP BY portfolio_id, week_start;

CREATE MATERIALIZED VIEW IF NOT EXISTS bipros_analytics.mv_activity_weekly
ENGINE = AggregatingMergeTree
PARTITION BY toYYYYMM(week_start) ORDER BY (project_id, activity_id, week_start)
AS SELECT project_id, activity_id, toMonday(date) AS week_start,
          maxState(pct_complete_physical) AS pct_state,
          sumState(qty_executed) AS qty_state
FROM bipros_analytics.fact_activity_progress_daily GROUP BY project_id, activity_id, week_start;
