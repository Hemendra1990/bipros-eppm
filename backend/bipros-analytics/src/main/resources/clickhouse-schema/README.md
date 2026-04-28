# ClickHouse schema files

`ClickHouseSchemaBootstrapper` reads every `.sql` file in this directory in
alphabetical order at boot and executes its statements against the writer
DataSource. Idempotency is the contract — every statement must use
`CREATE TABLE IF NOT EXISTS` or `CREATE OR REPLACE VIEW`.

## Naming convention

| Range | Purpose |
|---|---|
| `000_*` | watermark / ETL-state tables |
| `001_*` … `099_*` | dimensions (`dim_*`) |
| `100_*` … `199_*` | facts (`fact_*`) |
| `900_*` … `999_*` | views — one file per role tier |

Lower numbers run first, so dimension tables exist by the time fact tables
reference them, and views are created last when every base table is in place.

## Engine pattern

All facts and dims are `ReplacingMergeTree(updated_at)`. Re-inserts of the same
key collapse to the row with the newest `updated_at` on the next merge. Views
read with `SELECT … FROM <table> FINAL` to force the collapse at query time.

## Column-tier rules (FLS via per-role views)

Three views per fact/dim — `vw_<t>_finance` (full), `vw_<t>_internal`
(money columns dropped), `vw_<t>_public` (identifiers + dates only).

**Always finance-only** — any column whose name contains:
`value`, `cost`, `amount`, `price`, `rate`, `pct`, `salary`, `wage`,
`budget`, `forecast`, `eac`, `etc`, `bac`, `pv`, `ev`, `ac`, `cv`, `sv`,
`cpi`, `spi`, plus `dim_user.email`, `dim_user.mobile`, `dim_user.employee_code`.

**Internal** — dates, statuses, RAG, percent_complete, IDs, names.

**Public** — identifiers, statuses, dates only; strip resource / contractor /
user names.

## Schema evolution

For additive changes, use `ALTER TABLE <t> ADD COLUMN IF NOT EXISTS <c> <type>`.
Either append to the existing file or create a new numbered file
(e.g. `003a_dim_activity_add_calendar_id.sql`). Column drops or renames need a
new file with explicit `ALTER` + a backfill plan.

The bootstrapper is **not** a checksum-tracking migration system. We rely on
ClickHouse's own DDL idempotency keywords. If a deploy needs strict ordering or
rollback, switch to a real migrator (Liquibase or Flyway) — out of scope for
Phase 1.

## Empty-string sentinels

ClickHouse `ORDER BY` columns may not be `Nullable` without
`SETTINGS allow_nullable_key = 1`. For columns appearing in `ORDER BY` that are
nullable in Postgres (e.g. `fact_evm_snapshots.wbs_node_id`,
`fact_evm_snapshots.activity_id`), the table column is plain `String` and ETL
handlers send the empty string `""` when the source value is `null`. Queries
must filter with `WHERE <col> != ''` rather than `IS NOT NULL`.
