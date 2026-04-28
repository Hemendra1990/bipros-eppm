# GenAI Natural-Language Analytics Assistant — Implementation Plan

## Context

The user wants to add an "Analytics Assistant" to bipros-eppm where any signed-in user can ask natural-language questions about their projects and get answers — and (in a later phase) also issue commands like "create WBS X under project Y, add activities A/B/C, attach resource R to activity B". The core requirements:

1. **Natural language → query → result.** Question gets translated to a query, query runs, results come back as a chat response.
2. **Queries run against ClickHouse, not Postgres.** Postgres remains the OLTP system of record; ClickHouse is the analytical store, kept fresh from Postgres.
3. **Role-based authorization** is non-negotiable. Users only see / change what their roles allow. Polite refusal otherwise — never silent over-fetching.
4. **Both reads and writes** are in scope long-term, but **v1 is read-only**; writes deferred to v2.
5. **Per-user LLM provider with BYOK (Bring Your Own Key).** The user — not the platform — configures and pays for their LLM. Multi-provider abstraction (Anthropic / OpenAI / Google / locally-hosted) selectable per user, with a "default" pointer.
6. **ClickHouse self-hosted via docker compose** alongside the existing Postgres / Redis / MinIO services. Production deployment uses the same orchestrator the rest of bipros-eppm runs on.
7. **Data freshness:** micro-batch ETL with 5-15 min lag from Postgres to ClickHouse. (No Debezium/Kafka in v1.)

The existing `/analytics` page already has a wired chat UI scaffold (`frontend/src/app/(app)/analytics/page.tsx`), the backend has a stubbed `AnalyticsController` + `AnalyticsQueryService` doing keyword-based pattern matching against Postgres, and an Anthropic Claude vision integration is already proven for satellite imagery (`bipros-integration/.../ClaudeVisionAnalyzer.java`). The hardcoded `userId = "anonymous"` in the current analytics service is a known bug that gets fixed in Phase 0.

The plan is structured as **6 phases**, each independently shippable, with verification gates at each end. Phases 0-4 are v1 (read-only). Phase 5 is v2 (writes, separately planned later).

---

# Architectural overview

Five logical layers, isolated by clear interfaces:

```
┌─────────────────────────────────────────────────────────────────────┐
│ FRONTEND (Next.js)                                                  │
│  /analytics page (existing scaffold)                                │
│  /settings/llm-providers (NEW — BYOK config)                        │
└─────────────────────────────────────────────────────────────────────┘
                              ↓ POST /v1/analytics/query
┌─────────────────────────────────────────────────────────────────────┐
│ BACKEND ORCHESTRATOR (bipros-analytics, NEW module)                 │
│  AnalyticsAssistantService                                          │
│   1. Resolve current user + roles + accessible projects (Redis)     │
│   2. Resolve user's chosen LLM provider + decrypt API key           │
│   3. Build prompt (system + tool registry + cached schema digest)   │
│   4. Call provider via LlmAdapter (interface)                       │
│   5. Parse structured tool-call response                            │
│   6. Validate + dispatch tool                                       │
│   7. Format result + persist audit log                              │
└─────────────────────────────────────────────────────────────────────┘
              ↓ tool dispatch                  ↓ audit
┌──────────────────────────────────┐    ┌──────────────────────────┐
│ TOOL REGISTRY                    │    │ AUDIT TRAIL              │
│  curated read tools (8 in v1)    │    │  analytics_audit_log     │
│  + execute_sql fallback          │    │  every query, plan, tool │
│  each tool re-checks auth        │    │  call, SQL hash, result  │
└──────────────────────────────────┘    │  hash, latency, tokens   │
              ↓                          └──────────────────────────┘
┌──────────────────────────────────┐
│ CLICKHOUSE QUERY LAYER           │
│  SQL parser (Calcite) — validate │
│  Inject project_id IN(allowed)   │
│  Read-only DB user               │
│  Timeout 10s, row cap 10K        │
└──────────────────────────────────┘
              ↓
       ┌──────────────┐         ┌────────────────────────┐
       │  CLICKHOUSE  │ ←—ETL—— │  POSTGRES (OLTP)       │
       │  star-schema │ 5-15min │  unchanged             │
       │  fact_*      │         │  source of truth       │
       │  dim_*       │         └────────────────────────┘
       │  agg_*       │
       └──────────────┘
```

**Key isolation:** the orchestrator never touches Postgres for analytics. ETL is the only crossing point. This means Postgres performance is not affected by analytics traffic.

---

# Phase 0 — Foundations (1-2 weeks)

**Goal:** Stand up the docker-compose ClickHouse, the BYOK provider model, and the new `bipros-analytics` module skeleton. Nothing user-facing yet.

## 0.1 Add ClickHouse to docker-compose

**File:** `docker-compose.yml` — add a new service:

```yaml
clickhouse:
  image: clickhouse/clickhouse-server:24.8
  container_name: bipros-clickhouse
  ports:
    - "8123:8123"   # HTTP/JDBC
    - "9001:9000"   # native protocol (note: 9000 used by MinIO; remap)
  ulimits:
    nofile: { soft: 262144, hard: 262144 }
  volumes:
    - clickhouse_data:/var/lib/clickhouse
    - ./docker/clickhouse-init:/docker-entrypoint-initdb.d
  environment:
    CLICKHOUSE_USER: bipros
    CLICKHOUSE_PASSWORD: bipros_dev
    CLICKHOUSE_DB: bipros_analytics
  healthcheck:
    test: ["CMD", "wget", "--spider", "-q", "http://localhost:8123/ping"]
    interval: 10s
    timeout: 5s
    retries: 5

volumes:
  clickhouse_data:
```

**Files:** `docker/clickhouse-init/01-create-databases.sql` (creates `bipros_analytics`, creates read-only user `bipros_reader` with SELECT-only grant for the LLM query path).

**Verification:** `docker compose up -d clickhouse && curl http://localhost:8123/ping` returns "Ok".

## 0.2 Create `bipros-analytics` Maven module

A new module under `backend/` with the standard DDD layout. Add to `backend/pom.xml` modules list and to `bipros-api/pom.xml` as a dependency.

**Dependencies to add to `bipros-analytics/pom.xml`:**
- `com.clickhouse:clickhouse-jdbc:0.6.5` (JDBC driver)
- `org.springframework:spring-jdbc` (we use plain JdbcTemplate against ClickHouse, not JPA)
- `org.apache.calcite:calcite-core:1.37.0` (SQL parser for safety validation)
- `org.springframework.security:spring-security-crypto` (already transitively available, used for AES-GCM key encryption)

## 0.3 BYOK — User LLM Provider model

**New entity:** `UserLlmProvider` (in `bipros-security` since it's auth-adjacent, or in `bipros-analytics` — recommend the latter to keep crypto concerns isolated).

```java
@Entity @Table(name = "user_llm_providers", schema = "analytics")
class UserLlmProvider extends BaseEntity {
  UUID userId;                           // FK to security.users
  LlmProvider provider;                  // enum: ANTHROPIC, OPENAI, GOOGLE, MISTRAL, OLLAMA, AZURE_OPENAI
  String modelName;                      // e.g. "claude-sonnet-4-6", "gpt-4o-2024-08-06"
  String displayName;                    // user-given label e.g. "My Anthropic key (work)"
  byte[] encryptedApiKey;                // AES-256-GCM encrypted; null for OLLAMA local
  String endpointOverride;               // for Azure / self-hosted endpoints; nullable
  boolean isDefault;                     // exactly one true per userId; partial unique index
  ProviderStatus status;                 // ACTIVE, DISABLED, KEY_INVALID (set by background validator)
  Instant lastValidatedAt;
}
```

**Key encryption:** AES-256-GCM with a master key from env var `BIPROS_LLM_KEK_HEX` (32 bytes hex). Service: `LlmKeyVault` with `encrypt(plaintext)` / `decrypt(ciphertext)` methods. Never log keys. The DTO returned to the UI shows `apiKeyConfigured: true/false` only — never the key itself.

**Liquibase changeset:** `backend/bipros-api/src/main/resources/db/changelog/050-user-llm-providers.yaml`. Includes a partial unique index `(user_id) WHERE is_default = true`.

## 0.4 LLM provider abstraction

**Interface** in `bipros-analytics/.../llm/`:

```java
interface LlmAdapter {
  LlmProvider provider();
  CompletionResponse complete(CompletionRequest req);
}

record CompletionRequest(
  String systemPrompt,
  List<Message> conversation,    // last N user/assistant turns
  List<ToolDefinition> tools,    // tool registry shape
  String cacheBreakpoint,        // for Anthropic prompt caching
  Duration timeout,
  Integer maxTokens
);

record CompletionResponse(
  ToolCall toolCall,             // structured tool invocation requested by LLM
  String narrative,              // optional commentary text
  TokenUsage tokens,
  Money costEstimate
);
```

**Implementations** (each ~150-300 lines):
- `AnthropicAdapter` — reuses HTTP pattern from `ClaudeVisionAnalyzer.java`. Adds prompt-caching breakpoints.
- `OpenAiAdapter` — uses `/v1/chat/completions` with `tools` + `tool_choice`.
- `OllamaAdapter` — local endpoint, no API key needed.
- `GoogleGeminiAdapter` — Gemini's tool-calling API.
- (Azure / Mistral / etc. can be added later via the same interface.)

**Resolution service:** `LlmProviderResolver.forUser(userId)` → returns the right `LlmAdapter` instance, decrypting the API key on the fly. If user has no provider configured, throws `LlmNotConfiguredException` (handled later as a polite UI message).

## 0.5 Frontend — LLM Provider Settings page

**New page:** `frontend/src/app/(app)/settings/llm-providers/page.tsx`. List the user's configured providers, with "Add provider" / "Set as default" / "Remove" / "Test connection" actions.

**Reachable from:** sidebar Settings menu, or via a "Configure your LLM" prompt on the Analytics page when the user has no provider yet.

**Test connection** — calls a backend endpoint `POST /v1/llm-providers/{id}/test` that does a lightweight ping (e.g., a `say "ok"` 1-token request) and stores `lastValidatedAt`.

## Phase 0 verification
- `docker compose up -d` brings up ClickHouse alongside the existing services.
- A user can log in to bipros-eppm, navigate to `/settings/llm-providers`, add an Anthropic key, mark it default, click Test → green checkmark.
- Database row `analytics.user_llm_providers` exists with encrypted key bytes (verify via `pgcli`).
- `curl http://localhost:8123/?user=bipros_reader` succeeds; DDL attempts fail (read-only user works).

---

# Phase 1 — ClickHouse analytical schema + ETL pipeline (2 weeks)

**Goal:** Get production-quality data into ClickHouse, refreshed every 5-15 min. No LLM yet.

## 1.1 Schema design — star-schema for analytics

**Not** a 1:1 mirror of Postgres. Optimised for the questions users ask. Tables go in the `bipros_analytics` ClickHouse database.

### Fact tables (one row per business event)
| Table | Grain | Source |
|---|---|---|
| `fact_evm_snapshots` | (project_id, wbs_node_id, activity_id, snapshot_date) | `evm.evm_calculations` |
| `fact_activity_progress` | (activity_id, snapshot_date) | `activity.activities` (snapshotted daily by ETL) |
| `fact_dpr_lines` | (dpr_id) | `project.daily_progress_reports` + `project.daily_resource_deployments` |
| `fact_activity_expenses` | (expense_id) | `cost.activity_expenses` |
| `fact_resource_assignments` | (assignment_id) | `resource.resource_assignments` |
| `fact_baseline_variance` | (project_id, baseline_id, activity_id, snapshot_date) | computed from `baseline.baseline_activities` + current `activity.activities` |
| `fact_risks` | (risk_id) | `risk.risks` (latest state per risk, updated on change) |
| `fact_contracts` | (contract_id) | `contract.contracts` |
| `fact_resource_daily_logs` | (resource_id, log_date) | `resource.resource_daily_logs` |
| `fact_equipment_logs` | (log_id) | `resource.equipment_logs` |
| `fact_labour_returns` | (id) | `resource.labour_returns` |

### Dimension tables (slowly-changing)
- `dim_project` — Project + EPS + OBS lookup (denormalised for fast filtering).
- `dim_wbs` — WBS hierarchy with parent path for tree queries.
- `dim_activity` — Activity master with type / status.
- `dim_resource` — Resource master with category / ownership.
- `dim_user` — Users for "who did what" filtering.
- `dim_calendar`, `dim_eps_node`, `dim_obs_node`, `dim_organisation`, `dim_currency`.

### Aggregate / materialised views (pre-computed)
- `agg_monthly_evm_by_project`
- `agg_monthly_cost_by_wbs`
- `agg_supervisor_daily_pl` (in scope after the DPR/DBS work — not v1)
- `agg_resource_utilization_weekly`

**Engine choices:**
- Fact tables: `ReplacingMergeTree(updated_at)` keyed on the natural PK + business date — handles the upsert semantics from Postgres ETL.
- Dimension tables: `ReplacingMergeTree(updated_at)` on PK.
- Aggregates: `SummingMergeTree` or `AggregatingMergeTree` for fast rollup queries.

**Schema location:** `backend/bipros-analytics/src/main/resources/clickhouse-schema/` — versioned `.sql` files. The orchestrator runs them on startup if absent (a "ClickHouse schema bootstrapper").

## 1.2 ETL orchestrator

**Service:** `EtlOrchestrator` (Spring `@Scheduled`).

**Pattern: watermark-based incremental sync.**
- Each fact-table sync handler keeps its high-watermark in a ClickHouse table `etl_watermarks(table_name, last_synced_at, rows_pulled, last_run_status, last_run_message)`.
- On each tick (every 10 min by default — configurable via `bipros.analytics.etl.cron`):
  1. Read the watermark.
  2. Query Postgres for rows where `updated_at > watermark` (using the `BaseEntity.updatedAt` field every entity has).
  3. Convert to ClickHouse-friendly column types (UUIDs as strings, BigDecimal as Decimal128, instants as DateTime64).
  4. INSERT into a staging table (`stg_<table>` with same shape).
  5. INSERT INTO ... SELECT FROM staging into the production fact/dim table.
  6. Update watermark.

**Per-table sync handlers** (one per fact/dim table). Each is a small class implementing:
```java
interface SyncHandler {
  String tableName();
  Duration cadence();           // some tables sync every 5 min, some every hour
  SyncReport sync(Instant since);
}
```

**Backfill mode:** initial deployment needs a full pull. Add a `POST /v1/admin/analytics/backfill?table=fact_evm_snapshots` admin endpoint that triggers a full re-pull (truncate staging + INSERT all rows + atomic swap).

**Observability:**
- Spring Actuator endpoint `/actuator/etl-health` exposes per-table watermark age + last-run status.
- Logs every sync run with row counts and latency.
- A Prometheus-style metric `bipros_etl_lag_seconds{table=...}` for alerting (deferred to ops setup).

## 1.3 Field-level masking on ClickHouse side

To support the FLS layer:
- Create per-role views: `vw_fact_contracts_finance` (full columns) vs `vw_fact_contracts_internal` (masked finance fields) vs `vw_fact_contracts_public` (only non-financial columns).
- The query orchestrator picks the right view set based on the caller's roles.
- Alternative: keep a single table and have the SQL filter inject a column-projection rewrite. Views are simpler and easier to audit; recommend views for v1.

## Phase 1 verification
- ETL runs every 10 minutes; watermarks update correctly.
- After seeding NHAI/Odisha demo data in Postgres + running ETL once, ClickHouse has matching row counts (`SELECT count(*) FROM fact_evm_snapshots` matches Postgres EVM snapshot count within ETL lag).
- Mutating a Project record in Postgres and waiting 10 min reflects in ClickHouse `dim_project`.
- Backfill endpoint resets and replays a single table cleanly.

---

# Phase 2 — Tool-calling LLM core (2-3 weeks)

**Goal:** End-to-end natural-language read pipeline. User asks question → answer comes back, with auth honoured.

## 2.1 Tool registry (v1, read-only)

A registered set of typed analytical primitives the LLM can call. Each tool:
- Has a JSON schema for inputs (validated by the orchestrator).
- Hits ClickHouse (not Postgres).
- Re-asserts authorization (calls `ProjectAccessService.requireRead(userId, projectId)` for any project-scoped tool).
- Returns structured data + a short natural-language summary the LLM can compose into the final response.

### v1 tool list (8 tools)

| # | Tool name | Purpose | Auth check |
|---|---|---|---|
| 1 | `list_projects` | Return projects accessible to the user (filtered, paginated) | RLS via accessible_project_ids |
| 2 | `get_project_overview` | Project header + active baseline + latest KPIs | requireRead(projectId) |
| 3 | `get_evm_snapshot` | EVM time series for a project + WBS or activity | requireRead(projectId) |
| 4 | `get_schedule_variance` | Variance vs baseline (per activity, filterable by critical/slipped/etc.) | requireRead(projectId) |
| 5 | `get_cost_variance` | Cost variance vs baseline | requireRead(projectId) |
| 6 | `get_top_risks` | Top-N risks (by score / by RAG / by status) | requireRead(projectId) (or null = portfolio if the user has cross-project rights) |
| 7 | `get_resource_utilisation` | Utilisation by resource / role / equipment over a date range | requireRead on project filter |
| 8 | `execute_sql` | **Fallback** — read-only ad-hoc SQL against ClickHouse for questions tools don't cover. Heavily guarded (see 2.3). | Project-scope filter injected at parse-time |

Each tool lives as a Spring service method:
```java
@AnalyticsTool(name = "get_schedule_variance", roles = {"VIEWER", "PROJECT_MANAGER", ...})
public ToolResult getScheduleVariance(GetScheduleVarianceRequest req, AuthContext auth) { ... }
```

A `ToolRegistry` autowires all `@AnalyticsTool`-annotated methods at startup, builds the JSON schema for each (using `jackson-module-jsonSchema` or hand-rolled), and exposes them to the LLM in the system prompt.

## 2.2 The orchestrator flow

```
1.  HTTP: POST /v1/analytics/query { query: "...", projectContext?: UUID }
2.  Resolve currentUser from JWT (replace the hardcoded "anonymous")
3.  Resolve LlmAdapter via LlmProviderResolver.forUser(userId)
    → if user has no provider configured: return polite "Configure an LLM provider first" with a link to /settings/llm-providers
4.  Load conversation history (last 5 turns) from analytics_queries
5.  Build CompletionRequest:
    - System prompt = template(roles, accessible_project_count, current_date, available_tools_with_schemas, schema_digest_for_clickhouse)
    - User prompt = current query + context hints
    - Cache breakpoint after the schema_digest section (Anthropic prompt caching saves ~50% on cost)
6.  Call adapter.complete(req)
7.  Parse the LLM's tool-call response
8.  Validate:
    - Tool name in registry?
    - User's role authorised for this tool?
    - Inputs match the JSON schema?
9.  Dispatch tool — runs against ClickHouse with project-scope injection
10. Format result (table + narrative)
11. Persist audit log row + analytics_queries row
12. Return: { narrative, table, sqlExplanation, toolUsed, tokensUsed, costMicros }
```

## 2.3 SQL safety layer (for the `execute_sql` fallback)

The `execute_sql` tool is the riskiest. Even though the LLM is not generating SQL for the curated tools, the fallback exists for queries our tools don't cover. Multiple layers of defence:

1. **Parser validation (Apache Calcite):** parse the SQL, reject anything that isn't a SELECT or contains DDL/DML keywords.
2. **Allowlisted tables:** only `fact_*`, `dim_*`, `agg_*` tables in `bipros_analytics` DB. Schema-walk the parsed AST and reject any table outside this list.
3. **Forced project-scope filter:** rewrite the AST to inject `WHERE project_id IN (<allowed_set>)` on every base table that has a `project_id` column. Done with Calcite's RelNode rewrite.
4. **Read-only ClickHouse user:** the JDBC connection used for tool dispatch authenticates as `bipros_reader` (no DDL/DML grants). Even a parser bypass can't damage data.
5. **Per-query limits:** `SET max_execution_time=10, max_result_rows=10000, max_memory_usage=1G` on every connection.
6. **Timeout & circuit breaker:** Resilience4j wrapper around the JDBC call.

This layer lives in `bipros-analytics/.../sql/SqlSafetyValidator` and `SqlScopeRewriter`.

## 2.4 Authorization integration

For every query, the orchestrator pre-loads:
- The user's roles (from JWT + DB).
- The user's accessible-project-ID set (from `ProjectAccessService.getAccessibleProjectIdsForCurrentUser()`).
- The role-derived JsonView tier (Public / Internal / FinanceConfidential / Admin).

**For tool calls:** each tool method calls `ProjectAccessService.requireRead(userId, projectId)` first, so the existing 5-layer security stack applies unchanged.

**For `execute_sql`:** the parsed AST is rewritten to inject `project_id IN (<allowed>)`. Plus, the column-projection list is filtered: if the caller is not FINANCE/ADMIN, columns marked `@FinanceConfidential` (we'll keep a metadata file mapping ClickHouse columns to roles) are stripped from the result before it goes back to the LLM.

**Polite refusal:** when a tool throws `AccessDeniedException`, the orchestrator catches it, asks the LLM to compose a friendly explanation ("You don't have access to project X. Contact your PMO if you need access."), and returns that. Never returns raw exception messages.

## 2.5 Audit trail

**New table** `analytics.analytics_audit_log`:
- id, userId, query_text, llm_provider, llm_model, tool_called, tool_args (json), sql_executed, sql_hash, result_row_count, result_hash, narrative_returned, tokens_input, tokens_output, cost_micros, latency_ms, status (SUCCESS / REFUSED / ERROR), error_kind, request_id, created_at.

Every query — successful or not — produces exactly one row. This is the system's audit ground truth, feeds usage analytics for billing visibility (when we add per-user usage stats), and supports incident forensics.

## Phase 2 verification
- A user with only TEAM_MEMBER role on Project Alpha asks "show me cost variance for Project Beta" → polite refusal.
- The same user asks "show me my project's cost variance" → answer comes back with Alpha's data only.
- A user without an LLM provider configured asks anything → polite "configure your provider" message + button.
- The `execute_sql` fallback rejects `DROP TABLE`, `INSERT`, `UPDATE`, queries against tables outside `bipros_analytics`.
- Audit log row exists for every query, including refusals.

---

# Phase 3 — Frontend wiring (1-2 weeks)

**Goal:** Make the Analytics page actually do the new thing, with transparency for the user.

## 3.1 Update `/analytics/page.tsx`

The chat scaffold is already there. Changes:
1. Replace the simple text rendering with a richer `AssistantMessage` component that supports:
   - Markdown narrative (use `react-markdown` + `remark-gfm`).
   - Tabular data (use the existing `DataTable` component).
   - Optional "Show SQL" disclosure showing what query ran (transparency).
   - Optional "Tool used: get_schedule_variance" badge.
2. Show "Tokens: 1,234 in / 567 out — Cost: $0.0023" footer per assistant message (BYOK transparency).
3. Suggested-queries chips remain but are populated by an endpoint `GET /v1/analytics/suggestions?context=<projectId?>` that returns context-aware suggestions (later — for v1 keep the existing 5).
4. Empty state when user has no LLM configured: a card with "Configure your LLM provider to enable the assistant" + button to `/settings/llm-providers`.

## 3.2 LLM Providers page

**File:** `frontend/src/app/(app)/settings/llm-providers/page.tsx`. Components:
- Provider list: code, displayName, model, isDefault badge, status (✓ valid / ⚠ key invalid / ⏸ disabled), last validated.
- "Add provider" modal: provider dropdown (Anthropic / OpenAI / Google / Mistral / Ollama / Azure OpenAI), model dropdown (filtered by provider), API-key input (password type), optional endpoint override, "Test connection" button.
- Edit / Set default / Remove actions inline.

## 3.3 API client

`frontend/src/lib/api/analyticsApi.ts` — add methods:
- `submitQuery(text, projectContext?)` — POST /v1/analytics/query
- `getQueryHistory(limit)` — already there
- `getProviders()` / `addProvider(...)` / `setDefault(id)` / `removeProvider(id)` / `testProvider(id)` — wired to the LLM provider controller.

## 3.4 Markdown / table component reuse

Reuse the existing variance-report `DataTable` component (`frontend/src/components/...`). For markdown rendering, add `react-markdown` + `remark-gfm` dependencies if not already present.

## Phase 3 verification
- Type "Show me the schedule variance for the NHAI project" → table appears with critical-path activities marked, narrative summary at the top, "SQL ran" disclosure shows the underlying query.
- A finance user sees contractValue in the response; a non-finance user sees the same response with contractValue masked (existing FLS layer).
- Click "Show SQL" reveals the actual ClickHouse SQL.
- Switch user's default provider from Claude to GPT-4o → next query uses OpenAI.

---

# Phase 4 — Observability + evaluation (1 week)

**Goal:** Build confidence in the system with measurable health and evaluation, before opening writes in v2.

## 4.1 Per-user usage dashboard

`/settings/llm-providers` gets a usage tab showing per-day-and-per-provider tokens and cost, computed from `analytics_audit_log`. Helps users self-monitor BYOK costs.

## 4.2 Admin observability

`/admin/analytics-health` page (admin-only): ETL watermark ages, query volume, error rate, average latency, top users by query count, top error patterns. Powered by aggregations over `analytics_audit_log` and `etl_watermarks`.

## 4.3 Evaluation harness

A test asset of ~50 known questions with expected results — covering cost, schedule, risk, resources, edge cases (refusals, SQL fallback). Stored as fixtures in `backend/bipros-analytics/src/test/resources/eval-set/`. A nightly run logs metrics: pass rate, refusal rate, hallucination rate, average latency, average cost per query.

## 4.4 Rate limiting

Per-user rate limit (e.g., 60 queries / hour, configurable) using a Redis-backed sliding-window counter. Polite "rate-limited, try again in N seconds" response if exceeded. Goal: prevent runaway costs from a misbehaving client.

## Phase 4 verification
- Eval suite ≥ 90% pass rate.
- Per-user cost dashboard renders correctly with seeded queries.
- Admin observability page surfaces ETL lag, errors, top patterns.
- Hammering the API with 100 queries in a minute triggers rate-limit at the configured threshold.

---

# Phase 5 — v2 writes via tool-calling (separate plan, ≥ 4 weeks; not implemented in this round)

**Goal noted but explicitly deferred.** Adds write-capable tools using a "plan-then-confirm" pattern:
- Curated set of writes (start with safe additions: DPR rows, expenses, single risks; add WBS/activity creation later).
- LLM produces a structured `ActionPlan` (list of typed tool calls). The UI shows the plan, asks the user to confirm.
- Each tool re-asserts authorization via existing `ProjectAccessService.requireEdit/Delete`.
- Side-effects audited the same way reads are.
- High-blast-radius operations (project archive, baseline activate, scoring matrix changes) require explicit confirm + extra role check (PMO/Admin only).

When v1 is shipped and stable, write a separate plan for v2.

---

# Critical files / areas to be created or modified

## New files (created)
- `docker-compose.yml` — add `clickhouse` service.
- `docker/clickhouse-init/01-create-databases.sql` — DBs + read-only user.
- `backend/bipros-analytics/` — entire new module:
  - `pom.xml` — module manifest.
  - `domain/model/UserLlmProvider.java`, `LlmProvider.java` (enum), `ProviderStatus.java`.
  - `domain/repository/UserLlmProviderRepository.java`.
  - `infrastructure/crypto/LlmKeyVault.java` — AES-GCM service.
  - `infrastructure/llm/LlmAdapter.java` (interface).
  - `infrastructure/llm/AnthropicAdapter.java`, `OpenAiAdapter.java`, `OllamaAdapter.java`, `GoogleGeminiAdapter.java`.
  - `application/service/LlmProviderResolver.java`, `LlmProviderService.java`.
  - `application/service/AnalyticsAssistantService.java` — the orchestrator.
  - `application/tool/ToolRegistry.java`, `@AnalyticsTool` annotation, `ToolDispatcher.java`.
  - `application/tool/ListProjectsTool.java`, `GetProjectOverviewTool.java`, ... (8 tools total).
  - `infrastructure/clickhouse/ClickhouseSchemaBootstrapper.java` — runs DDL on startup.
  - `infrastructure/clickhouse/ClickhouseTemplate.java` — JdbcTemplate wrapper, read-only user.
  - `application/sql/SqlSafetyValidator.java` (Calcite parser), `SqlScopeRewriter.java`.
  - `application/etl/EtlOrchestrator.java`, `SyncHandler.java` (interface), per-table handlers under `etl/handler/`.
  - `application/audit/AnalyticsAuditService.java`, entity `AnalyticsAuditLog.java`.
  - `presentation/controller/LlmProviderController.java`, `AnalyticsHealthController.java`.
  - `src/main/resources/clickhouse-schema/*.sql` — DDL for fact/dim/agg tables.
  - `src/test/resources/eval-set/` — evaluation suite.
- `backend/bipros-api/src/main/resources/db/changelog/050-user-llm-providers.yaml`.
- `backend/bipros-api/src/main/resources/db/changelog/051-analytics-audit-log.yaml`.
- `frontend/src/app/(app)/settings/llm-providers/page.tsx`.
- `frontend/src/components/analytics/AssistantMessage.tsx` (markdown + table renderer).
- `frontend/src/lib/api/llmProvidersApi.ts`.

## Existing files (modified)
- `backend/bipros-reporting/.../AnalyticsController.java` — replace processQuery() body to delegate to new `AnalyticsAssistantService`. Also fix the `userId = "anonymous"` bug — pull from `CurrentUserService`.
- `backend/bipros-reporting/.../AnalyticsQueryService.java` — keep as a fallback / legacy classifier or remove once new pipeline is stable.
- `frontend/src/app/(app)/analytics/page.tsx` — wire to new pipeline; render markdown / tables / SQL disclosure.
- `frontend/src/lib/api/analyticsApi.ts` — extend response shape to include `narrative`, `table`, `toolUsed`, `sqlExecuted`, `tokensUsed`, `costMicros`.
- `backend/pom.xml` — add `bipros-analytics` to modules list.
- `backend/bipros-api/pom.xml` — add `bipros-analytics` dependency.
- `backend/bipros-api/src/main/resources/application.yml` — new `bipros.analytics.*` config block (ClickHouse URL, ETL cron, KEK env var name, rate limits).
- `docker/init-schemas.sql` — add the `analytics` schema for the Postgres-side tables (`user_llm_providers`, `analytics_audit_log`).
- Sidebar navigation in frontend — add "LLM Providers" under Settings.

## Existing services / utilities to **reuse, not duplicate**
- `ClaudeVisionAnalyzer` (HTTP pattern) — `AnthropicAdapter` mirrors this.
- `ProjectAccessService.requireRead/Edit/Delete()` — every tool calls these.
- `ProjectAccessService.getAccessibleProjectIdsForCurrentUser()` — feeds the SQL scope-rewriter.
- `AccessSpecifications.projectScopedTo(...)` — for any tool that uses Postgres directly (none in v1, but useful pattern).
- `Views` markers + `RoleAwareViewAdvice` — the response shape from tools should still flow through Spring's serialisation so FLS applies automatically.
- `CurrentUserService` — for user resolution.
- `BaseEntity` audit fields (`updatedAt`) — drives the ETL watermark.
- `ApiResponse<T>` envelope — all new controllers wrap responses in it.
- `GlobalExceptionHandler` — extend with `LlmNotConfiguredException`, `LlmRateLimitedException` handlers returning friendly 4xx responses.
- Existing `analytics_queries` table — keep populating it for conversation history; pair with the new `analytics_audit_log` for full forensics.

---

# Verification of the whole assistant end-to-end

1. **Cold start:** `docker compose up -d` → Postgres + ClickHouse + MinIO + Redis up.
2. **Backend boot:** `mvn -pl bipros-api spring-boot:run` → ClickHouse schema bootstrapper creates all `fact_*`/`dim_*` tables, ETL orchestrator runs first sync.
3. **Frontend:** `pnpm dev` → log in as `admin/admin123`.
4. **Configure LLM:** `/settings/llm-providers` → Add Anthropic → paste a test key → Test connection → green check.
5. **Ask:** `/analytics` → "What is the cost overrun risk on the NHAI project?" → assistant returns narrative + table + "SQL ran" disclosure.
6. **Switch user:** log in as a TEAM_MEMBER on a different project → ask the same question → polite refusal because they don't have access to NHAI.
7. **Switch provider:** add an OpenAI key, set as default → next query goes through GPT-4o.
8. **Edit Postgres data:** create a new activity in the NHAI project. Wait 10 minutes. Ask "list the latest activities on NHAI" → new activity appears (proves ETL freshness).
9. **Eval suite:** `mvn test -pl bipros-analytics -Dtest=EvalSuite` → pass rate ≥ 90%.
10. **SQL fallback safety:** ask a question that triggers `execute_sql` then deliberately try a malicious follow-up like "drop the contracts table" → polite refusal, parser blocks it.
11. **Audit log:** every query above appears in `analytics.analytics_audit_log` with full payload.
12. **Cost transparency:** `/settings/llm-providers` → Usage tab shows tokens / cost since the start of the test session.

---

# Open trade-offs noted (not blockers)

These are decisions that don't change the plan structure but should be revisited during implementation:

1. **Apache Calcite vs jOOQ for SQL parsing.** Calcite is the standard for SQL rewrite; jOOQ is more ergonomic. Calcite recommended for v1 because it has the best AST manipulation API.
2. **Markdown rendering library.** `react-markdown` + `remark-gfm` is the most common; consider hardening against XSS by configuring `rehype-sanitize`.
3. **Conversation memory size.** Last 5 turns in v1; we'll see what happens to context length as users have longer sessions. Anthropic's prompt caching makes longer contexts cheaper.
4. **Cost-cap per query.** With BYOK we don't have to worry about runaway platform cost, but a query that the LLM iterates on for too long can still hit user costs hard. Add a `maxTokensPerQuery` cap (e.g., 8K out) plus a hard execution-loop cap (max 3 tool-call rounds per query).
5. **Self-hosted Ollama:** if a user picks Ollama, we need a way to resolve their endpoint (e.g., `http://host.docker.internal:11434` from inside the container). Add an "endpoint override" field on the provider config — already in the schema.
6. **Schema digest size.** With many fact/dim tables, the schema description in the system prompt could be huge. Use Anthropic prompt caching for the schema-digest section. For non-Anthropic providers without caching, accept the higher cost or compress the digest to "just table names + key columns" until the LLM asks for more detail.

---

# Ordering / handover note

Once this plan is approved, the implementation follows phases 0 → 1 → 2 → 3 → 4 in order. Each phase is independently shippable behind a feature flag (`bipros.analytics.assistant.enabled=false` in v1 default). The plan file location: this draft is at `~/.claude/plans/`. Per the user's earlier preference, **after approval the plan should be moved to `docs/GENAI_ANALYTICS_IMPLEMENTATION_PLAN.md`** in the project repo so it's version-controlled with the code.
