CREATE TABLE IF NOT EXISTS dim_user (
    id                    String,
    username              String,
    full_name             Nullable(String),
    first_name            Nullable(String),
    last_name             Nullable(String),
    email                 Nullable(String),
    mobile                Nullable(String),
    employee_code         Nullable(String),
    designation           Nullable(String),
    primary_icpms_role    Nullable(String),
    department            LowCardinality(Nullable(String)),
    organisation_id       Nullable(String),
    presence_status       LowCardinality(Nullable(String)),
    joining_date          Nullable(Date),
    contract_end_date     Nullable(Date),
    enabled               UInt8,
    account_locked        UInt8,
    created_at            DateTime64(3),
    updated_at            DateTime64(3)
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY id
SETTINGS index_granularity = 8192;
