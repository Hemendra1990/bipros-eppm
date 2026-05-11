# Local ClickHouse re-init

If the AI chat logs show errors like:

- `Unknown expression identifier 'uom'`
- `Unknown expression identifier 'unit_rate'`
- `Identifier 'r.resource_id' cannot be resolved from table with name r`
- `There is no supertype for types UUID, String ... cannot infer common type in ON section`

…your local ClickHouse `dim_resource` (or another dim/fact table) was created
from an older init script and hasn't been re-applied. The code in
`docker/clickhouse-init.sql` is the source of truth; your runtime CH instance
drifted.

This is a **dev-environment-only** problem. The fix is one drop-and-replay.

## Fix

1. Stop the backend (let IntelliJ stop it, or `Ctrl+C` in the terminal).

2. Drop the analytics schema and replay the init script:

   ```bash
   # Drop everything analytics
   docker exec -i bipros-clickhouse clickhouse-client --multiquery \
     <<< "DROP DATABASE IF EXISTS bipros_analytics;"

   # Replay the current init script
   docker exec -i bipros-clickhouse clickhouse-client --multiquery \
     < docker/clickhouse-init.sql
   ```

   Equivalent PowerShell:

   ```powershell
   docker exec -i bipros-clickhouse clickhouse-client --multiquery `
     --query "DROP DATABASE IF EXISTS bipros_analytics;"

   Get-Content docker/clickhouse-init.sql | `
     docker exec -i bipros-clickhouse clickhouse-client --multiquery
   ```

3. Restart the backend.

4. Repopulate the dim tables immediately (don't wait for the 01:30 UTC cron):

   ```bash
   curl -X POST http://localhost:8080/v1/admin/analytics/resync-dimensions \
        -H "Authorization: Bearer <your admin JWT>"
   ```

   This calls `DimensionSyncJob.run()` directly and repopulates `dim_project`,
   `dim_wbs`, `dim_activity`, `dim_resource`, `dim_cost_account`, `dim_calendar`,
   `dim_risk`, `dim_permit*`, `dim_labour_designation`.

5. Optionally backfill historical facts (DPR, activity progress, cost, EVM, risk):

   ```bash
   curl -X POST "http://localhost:8080/v1/admin/analytics/backfill?fact=all&from=2026-01-01&to=2026-12-31" \
        -H "Authorization: Bearer <your admin JWT>"
   ```

   Fact tables also fill incrementally as new events fire (DPRs filed,
   assignments saved, cost recorded, EVM recalculated).

6. Verify: ask the AI chat *"equipment hours over the last 30 days"*. It
   should return a date-bucketed result (not "Unknown expression identifier").

## Why this happens

The init script (`docker/clickhouse-init.sql`) is mounted at
`/docker-entrypoint-initdb.d/init.sql` in the container. ClickHouse only runs
those scripts the **first time** the volume is initialized. Subsequent schema
changes — new columns, type adjustments — never get re-applied to a volume
that already has the database. There is currently no Flyway/Liquibase-for-CH
migration system; the manual drop-and-replay above is the dev workaround.

## What about production?

Production sets up ClickHouse separately and applies schema changes via the
team's deployment process (out of scope here). This doc is for dev/local only.

## Related code

- `docker/clickhouse-init.sql` — the authoritative DDL for every
  `bipros_analytics.dim_*` and `fact_*` table.
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/SchemaCatalog.java` —
  the catalog the AI orchestrator hands to the LLM; mirrors the init script
  exactly. If the LLM is shown a column that doesn't exist in CH, this catalog
  is wrong OR your local CH is stale (almost always the latter).
- `backend/bipros-analytics/src/main/java/com/bipros/analytics/etl/` —
  ETL listeners that populate fact tables when events fire (DPR submitted,
  EVM recalculated, cost expense recorded, etc).
- `DimensionSyncJob` (in `bipros-analytics`) — nightly cron that repopulates
  every `dim_*` table from the corresponding OLTP source.
