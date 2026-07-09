# Multi-Agent AI Platform — Implementation Handoff

**Date:** 2026-07-09
**Branch:** `khasab-demo-ready-2026-05-24-temp`
**Goal:** Transform the EPPM app into an AI-native "Project Intelligence Platform" — 11 specialized agents that proactively monitor projects, share a findings memory, predict problems, and notify stakeholders, plus an animated frontend (AI Overview, Forecast, live "agents working" feed).
**Source plan:** `~/.claude/plans/prompt-transform-the-unified-moon.md` (read it — it has the full design and pipeline definitions).

> This document is a self-contained handoff so a fresh session can finish the work. It records what is DONE and verified, what is IN FLIGHT, what REMAINS (Phase 2), the framework contracts you need to write more code, and the plan-vs-code corrections and repo gotchas that will otherwise bite you.

---

## 0. First actions for the next session (do these in order)

> **Phase 1 is COMPLETE and VERIFIED.** As of this handoff: `mvn -pl bipros-ai test-compile` → BUILD SUCCESS (292 main + 68 test sources); `mvn -pl bipros-ai test` (agent/router/coalescer/pipeline/memory/budget suites) → **53 tests, 0 failures**. Frontend `next build` → Compiled successfully, all 3 AI routes present. All 11 agents + Track A/C/D landed and green. You can go **straight to Phase 2 (§4)**.

1. **Sanity re-check** (fast — should already be green):
   ```bash
   cd backend && mvn -pl bipros-common install -DskipTests && mvn -pl bipros-ai test-compile 2>&1 | tail -20
   cd frontend && node node_modules/next/dist/bin/next build 2>&1 | grep -E "Compiled successfully|Failed|Type error"
   ```
   If anything is unexpectedly red, fix compile errors in `bipros-ai/.../agent/**` only (never change Phase 0 public contracts — fix the caller).
2. Then start **Phase 2** (§4). The highest-value first step is 4a (supervisor mode) — all contracts are ready.

---

## 1. Architecture (what was built and where)

Everything backend lives in the existing **`bipros-ai`** Maven module under `com.bipros.ai.agent.*`. Tables live in the existing `ai` Postgres schema with an `agent_` prefix. No new module, no `docker/init-schemas.sql` change.

```
backend/bipros-ai/src/main/java/com/bipros/ai/agent/
  core/       Agent, AbstractAgent, AgentRegistry, AgentRunContext, AgentFindingDraft,
              EvidenceRef, Severity, GatherResult, AgentNarrator, AgentRuntime,
              AgentEventHub (iface) + NoOpAgentEventHub, AgentStreamEvent
  domain/     AgentRun, AgentPipelineRun, AgentFinding, AgentInvestigation, AgentBudgetUsage,
              AgentTriggerQueueItem(+Id), AgentChannelConfig, AgentNotificationRule,
              AgentNotificationDelivery + repos + enums (AgentRunStatus, PipelineRunStatus,
              FindingStatus, LlmSkipReason, DeliveryStatus)
  memory/     AgentMemoryService (dedup/supersession/TTL), FindingFingerprint
  budget/     LlmBudgetGuard, AgentBudgetProperties
  pipeline/   PipelineDefinition, AgentPipelines, AgentPipelineRunner, AgentExecutorConfig, AgentRunService
  trigger/    AgentTriggerListener, AgentTriggerCoalescer, AgentTriggerDrainJob,
              AgentSweepJobs, AgentTriggerProperties
  stream/     SseAgentEventHub, AgentStreamController
  notify/     NotificationChannel, InAppChannel, EmailChannel, WhatsAppSmsChannel,
              MessagingProviderAdapter, TwilioMessagingProviderAdapter, NotificationRouter,
              StakeholderResolver, NotificationSseHub, NotificationStreamController,
              AgentDigestJob, AgentNotifyProperties, ResolvedNotification
  impl/       11 agents (see §3)
  api/        AgentController, AgentDtoMapper, dto/AgentDtos
```

**Design spine — deterministic-first:** each agent's `gather(ctx)` reads domain services and computes candidate findings using rule thresholds + a deterministic confidence (named in `confidenceBasis`). Then `AbstractAgent` makes **one** schema-strict LLM narration call (`AgentNarrator`, cloned from `InsightsGenerator`) that only rewords the prose — it can never invent numbers, add, or drop a finding. If the LLM is unconfigured / over budget / failing, the templated candidate prose is used verbatim; monitoring never blocks. `AbstractAgent` owns the whole run lifecycle: data-hash change-skip → budget reserve → narrate → memory upsert (dedup/supersession) → SSE lifecycle events. Concrete agents implement only `gather()`.

**Frontend** lives at `frontend/src/app/(app)/projects/[projectId]/ai/{page,forecast/page}.tsx`, `frontend/src/app/(app)/ai/page.tsx` (portfolio), and `frontend/src/components/ai/agents/*`. API layer `frontend/src/lib/api/agentApi.ts`, types `frontend/src/lib/types/agent.ts`.

---

## 2. Status by phase

### ✅ Phase 0 — foundation (DONE, verified: compiles, 11 unit tests pass, jar installed)
- Core contracts, 9 entities + repos in `ai` schema, `AgentMemoryService`, `FindingFingerprint`, `LlmBudgetGuard` (atomic reserve/record with pessimistic row locks), `AgentNarrator` (strict candidate-index `json_schema`, parse-retry, templated fallback), DTOs + `AgentController` (read + manual-run + ack/resolve), `canRead`/`canWrite` added to `AiAccessGuard`.
- Liquibase: **`118-agent-tables.yaml`** (all agent tables, partial-unique indexes on `agent_finding(fingerprint) WHERE status='ACTIVE'` and `agent_pipeline_run(pipeline_key,project_id) WHERE status='RUNNING'`) + **`119-user-notification-severity.yaml`** (nullable `severity`+`metadata` on `public.user_notifications`). Both registered in `db.changelog-master.yaml` after `117`.
- Config `bipros.agent.*` in `backend/bipros-api/src/main/resources/application.yml` (budget, trigger windows, schedule crons, notify routing + email).
- Tests: `FindingFingerprintTest`, `AgentMemoryServiceTest`, `LlmBudgetGuardTest`, `CapacityUtilisationAgentTest` all green.

### ✅ Phase 1 — Track A triggers/pipeline/stream (DONE, compiled in first workflow: BUILD SUCCESS + 40 tests)
`pipeline/` (PipelineDefinition, AgentPipelines constants, AgentPipelineRunner bounded-executor + idempotency, AgentExecutorConfig `agentTaskExecutor`), `trigger/` (AgentTriggerListener `@TransactionalEventListener(AFTER_COMMIT)` mapping 15 domain events → 3 reactive pipelines, AgentTriggerCoalescer DB-backed debounce, AgentTriggerDrainJob `@Scheduled`+lease, AgentSweepJobs crons+lease), `stream/` (SseAgentEventHub reactor sinks, AgentStreamController SSE endpoints).

### ✅ Phase 1 — Track B + gap-fill agents (all 11 present; DONE, 53 tests green)
All 11 agents exist in `impl/`: Capacity, Planning, DPR, DBS, Risk, GIS, Document (first workflow) + **Forecasting, Issue, Executive, Notification** (gap-fill). ExecutiveInsightsAgent + its test were written by the main thread inline. Per-agent unit tests exist and pass (ForecastingAgentTest, IssueIntelligenceAgentTest, ExecutiveInsightsAgentTest, DocumentIntelligenceAgentTest, GisIntelligenceAgentTest, etc.).

### ✅ Phase 1 — Track C notify (DONE, compiled + NotificationRouterTest green)
`notify/` fully populated (13 files) + `NotificationAgent` (#11, LLM-free, routes notifiable findings as a side effect and returns an empty `GatherResult`). `spring-boot-starter-mail` added to `bipros-ai/pom.xml`. `AgentFindingRepository` gained `findByProjectIdAndStatusAndNotifiableTrue` + `findByStatusAndNotifiableTrue` for routing/digest. Two events created: `bipros-common/.../event/DocumentUploadedEvent.java` + `GisSnapshotAnalyzedEvent.java` (compile-clean; publishers not yet wired — see §4c).

### ✅ Phase 1 — Track D frontend (DONE, verified: `next build` succeeds, all 3 AI routes present)
`agentApi.ts`, `types/agent.ts`, 8 components (`FindingCard`, `ConfidenceBadge`, `AgentActivityFeed`, `useAgentStream`, `AgentAvatar`, `AgentWorkingIndicator`, `InvestigatePanel`, `agentMeta`), 3 pages (project AI overview, forecast, portfolio). AI tab added to `projects/[projectId]/layout.tsx` (gated `AI.READ`) + mission-control tile in `modulesConfig.ts`.
> NOTE: the frontend agent leaked stray `</content>`/`</invoke>` XML tags into 13 files when it was interrupted; these were all stripped. If you re-run any subagent that writes files and it dies mid-response, grep the touched files for `</content>`, `</invoke>`, `</parameter>` and strip them.

### ✅ Extras done
`scripts/demo-agents.sh` (login → sweep → poll runs → print findings; `bash -n` clean).

---

## 3. The 11 agents (keys + finding types)

| key | class | finding types | primary data source |
|---|---|---|---|
| `capacity_utilisation` | CapacityUtilisationAgent (golden reference) | RESOURCE_OVERALLOCATION, IDLE_CAPACITY | `ResourceLevelingService.getUtilizationProfile(projectId)` |
| `planning_intelligence` | PlanningIntelligenceAgent | CRITICAL_PATH_SLIP, FLOAT_EROSION, BASELINE_DRIFT, LOGIC_QUALITY | ScheduleHealthService, BaselineService.getVariance + reads Capacity from memory |
| `dpr_intelligence` | DprIntelligenceAgent | DPR_MISSING, APPROVAL_BOTTLENECK | DailyProgressReportRepository |
| `dbs_validation` | DbsValidationAgent | NEGATIVE_CONTRIBUTION, MARGIN_DETERIORATION, DATA_QUALITY_GAP | DbsAlertEvaluator + DBS aggregate tables |
| `forecasting` | ForecastingAgent | COMPLETION_FORECAST, COST_AT_COMPLETION, CASHFLOW_PRESSURE | MonteCarloService, EvmService.computeEvmSnapshot (**flagship: confidence = MC P-value**) |
| `risk_intelligence` | RiskIntelligenceAgent | RISK_EXPOSURE_SPIKE, EMERGING_RISK, STALE_RISK_REVIEW | `RiskService.calculateRiskExposure(projectId)` (EMV) |
| `issue_intelligence` | IssueIntelligenceAgent | ISSUE_AGEING, RECURRING_ISSUE_PATTERN, HSE_OPEN_CRITICAL | DprIssueService + site-ops Ncr/Snag/SafetyRecord |
| `gis_intelligence` | GisIntelligenceAgent | FIELD_PROGRESS_MISMATCH, STRETCH_BEHIND | `ConstructionProgressService.getProgressVariance(projectId)` |
| `document_intelligence` | DocumentIntelligenceAgent | PERMIT_EXPIRY, DOCUMENT_SUMMARY, COMPLIANCE_DOC_GAP | DocumentService + bipros-permit |
| `executive_insights` | ExecutiveInsightsAgent (`supportsPortfolio=true`) | EXECUTIVE_BRIEF | reads ONLY agent memory (top-3 concerns) |
| `notification` | NotificationAgent (`supportsPortfolio=true`, LLM-free) | — (routes, produces no findings) | NotificationRouter over notifiable findings |

Pipelines (in `AgentPipelines`): `DAILY_PROJECT_SWEEP`, `OPERATIONS_REACTIVE`, `SCHEDULE_REACTIVE`, `RISK_REACTIVE`, `PORTFOLIO_WEEKLY`. Event→pipeline mapping is in `AgentTriggerListener`.

---

## 4. Phase 2 (convergence) — STATUS

> **Most of Phase 2 is DONE and compiling** (bipros-ai BUILD SUCCESS, 55 unit tests green, bipros-api aggregator compiles, frontend `next build` clean). Done: **4a supervisor mode** (RunAgentTool [note: one copy pre-existed from a parallel session — kept], ReadAgentFindingsTool, SupervisorService, SupervisorController `/investigate` SSE); **4b endpoints** (pipeline-run, `/v1/portfolio/agent-activity`, AgentAdminController channels+budgets); **4d frontend swaps** (ActivityTicker → live portfolio feed w/ seed fallback, NotificationBell → `/v1/notifications/stream` SSE w/ 45s poll fallback, agentApi `runPipeline`+`portfolioActivity`). **Bonus fix:** `AgentPipelines` had invalid keys (`executive_intelligence`/`portfolio_intelligence`) and omitted forecasting/executive/notification from stages — corrected to match the plan (every pipeline now ends with a `notification` stage; daily sweep runs …→forecasting→executive_insights→notification), guarded by a new `pipelinesReferenceOnlyRealAgentKeys` test.
>
> **STILL REMAINING** (needs a running stack or is a non-blocking enhancement):
> - **4c reactive Document/GIS triggers** — LOW priority. `document_intelligence` + `gis_intelligence` already run in the nightly `DAILY_PROJECT_SWEEP`, so this is only an on-upload speedup. To add: 2 new pipelines (DOCUMENT_REACTIVE/GIS_REACTIVE) + 2 handlers in `AgentTriggerListener` for the (already-created) `DocumentUploadedEvent`/`GisSnapshotAnalyzedEvent`, and publish them from `DocumentService.uploadDocument` / `ProgressAnalyzerService.analyzeAsync` (inject `ApplicationEventPublisher`; both have `projectId` + an id at the call site — verified).
> - **4e integration test** — write a Testcontainers (PG + Liquibase) test in bipros-api: publish `DprSubmittedEvent` → drain queue → assert `AgentPipelineRun`/`AgentRun` SUCCEEDED (`@MockitoBean OpenAiCompatibleProvider`) → `agent_finding` row → `user_notifications` row. Also validates Liquibase 118/119 apply.
> - **Live boot `/verify`** — bring up docker (PG+ClickHouse+MinIO+Redis) + `BIPROS_AI_KEK`, start bipros-api + frontend, run `scripts/demo-agents.sh`, click through the AI Overview/Forecast pages. Not doable without the running stack.
> - **Seed a default `LlmProviderConfig`** so narration works in the demo (needs a real provider key; agents fall back to templated prose without it — perfectly fine for a functional demo).

The original detailed specs for these are kept below for reference.

## 4b(ref). Original Phase 2 spec (for reference)

All backend code below goes in `bipros-ai`. After writing, run the §0 compile+test commands.

### 4a. Supervisor mode (LLM chats that can run agents as tools)
Contracts are ready — you have everything. Create `com.bipros.ai.agent.supervisor`:
- **`RunAgentTool`** — implement `com.bipros.ai.tool.Tool` (or extend `com.bipros.ai.tool.ProjectScopedTool`). `name()="run_agent"`, `isReadOnly()=false`, `inputSchema()` = `{agentKey:string, projectId?:string}`. `execute(input, ctx)` → build `AgentRunContext.manual(projectId, ctx.currentUserId?)` and call `AgentRunService.runSingle(agentKey, ctx)`, return `ToolResult.ok(summary, findingsJson)`. It becomes auto-registered because `ToolRegistry` injects all `Tool` beans — just annotate `@Component`.
- **`ReadAgentFindingsTool`** — `name()="read_agent_findings"`, read-only, input `{agentKey?:string, severity?:string}`, calls `AgentMemoryService.activeFindings(ctx.projectId(), agentKeys, minSeverity)`, returns `ToolResult.table(...)`.
- **`SupervisorService.investigate(question, ctx)`** → prepend a supervisor persona addendum (list the 11 agents + delegation guidance) and call the EXISTING `AiOrchestrator.handle(userMessage, imageUrl=null, history, ctx, provider, config)` → `Flux<ChatEvent>`. No new ReAct loop. Persist an `AgentInvestigation` row (question, answer, runIds, tokens).
- **Endpoint** `POST /v1/projects/{projectId}/agents/investigate` (SSE) guarded `@PreAuthorize("@aiAccess.canWrite(#projectId)")`, cloning `ChatController.chatStream` (`Flux<ServerSentEvent<String>>`). The frontend `InvestigatePanel` already calls this endpoint.

Key contracts (verified):
```java
// com.bipros.ai.tool.Tool
String name(); String description(); JsonNode inputSchema();
ToolResult execute(JsonNode input, AiContext ctx);
default boolean isReadOnly(){return true;} default Set<String> allowedRoles(){return Set.of();}
// com.bipros.ai.tool.ToolResult
ToolResult.ok(summary, data) / ok(summary) / error(err) / table(summary, ArrayNode rows, String[] cols)
// com.bipros.ai.tool.ProjectScopedTool: validates ctx.projectId() ∈ ctx.scopedProjectIds() (unless ADMIN), then doExecute()
// AiContext: projectId(), role(), scopedProjectIds(), (see com.bipros.ai.context.AiContext)
// AiOrchestrator.handle(String userMessage, String imageUrl, List<LlmProvider.Message> history, AiContext ctx, LlmProvider provider, LlmProviderConfig config) -> Flux<ChatEvent>
// Resolve provider+config like ChatController.resolveConfig()/llmProvider (OpenAiCompatibleLlmProvider bean)
```

### 4b. Missing REST endpoints (add to a controller in `api/`)
- `POST /v1/projects/{projectId}/agents/pipelines/{key}/run` → 202 `{pipelineRunId}` — call `AgentPipelineRunner.run(key, projectId, "MANUAL", ...)`. `@aiAccess.canWrite`.
- `GET /v1/portfolio/agent-activity` — recent runs/findings feed for `ActivityTicker`. (Simplest: latest N `AgentRun` rows across accessible projects → small DTO.)
- Admin: `GET|PUT /v1/admin/agent-channels` and `GET|PUT /v1/admin/agent-budgets` — CRUD over `AgentChannelConfig` (encrypt auth token with `ApiKeyCipher`) and `AgentBudgetProperties`/`AgentBudgetUsage`. Guard `@PreAuthorize("hasAuthority('ADMIN_MASTER.READ')")` / `'ADMIN_MASTER.UPDATE'` (**NOTE: `ADMIN_MASTER.WRITE` does NOT exist** — see §6).

### 4c. Wire the two new events (publishers)
Publish `DocumentUploadedEvent` from `DocumentService` (on upload/version) and `GisSnapshotAnalyzedEvent` from `ProgressAnalyzerService` (after analysis). **Verify the call sites first.** Then add handlers in `AgentTriggerListener` mapping them to a pipeline (Document/GIS single-agent runs). The nightly sweep already covers these agents, so this is an enhancement, not a blocker.

### 4d. Frontend SSE swaps + admin pages (need 4b endpoints first)
- `ActivityTicker.tsx` (`components/hub/mission-control/`) — replace `SEED_EVENTS` with a react-query on `GET /v1/portfolio/agent-activity`; keep a graceful empty fallback.
- `NotificationBell.tsx` (`components/common/`) — add a `useNotificationStream()` hook on `GET /v1/notifications/stream` (endpoint EXISTS: `NotificationStreamController`), keep the current 45s poll as fallback. Clone the SSE fetch-stream from `agentApi.streamAgents` / `aiApi.streamChat` (EventSource can't send Bearer).
- Admin channel/budget pages under `frontend/src/app/(app)/admin/` calling 4b admin endpoints.
- Also publish a `NotificationCreatedEvent` from `NotificationService.create` (one-line in bipros-common) so DPR/permit notifications push over SSE too (optional).

### 4e. Tests + verification
- Integration test (bipros-api, Testcontainers PG + Liquibase — validates the changelogs): `DprSubmittedEvent` → coalescer queue → drain → `AgentPipelineRun`/`AgentRun` SUCCEEDED (`@MockitoBean OpenAiCompatibleProvider`) → `agent_finding` row → SSE event. This also proves Liquibase 118/119 apply.
- Frontend: vitest for `FindingCard`/`ConfidenceBadge`/`useAgentStream`; Playwright login → AI Overview → Run sweep → RUNNING feed → finding card.
- Boot the stack and run `scripts/demo-agents.sh`. Seed a default `LlmProviderConfig` for narration (agents fall back to templates without it).
- Final: `mvn clean install` (full — catches the stale-M2 hazard), `pnpm build`, `/verify` end-to-end before claiming done.

---

## 5. Framework cheat sheet (to write agents / tools)

Golden reference to copy: `backend/bipros-ai/src/main/java/com/bipros/ai/agent/impl/CapacityUtilisationAgent.java` + its test. Memory-reading agent + its test (reflection-injects `AgentRuntime`): `PlanningIntelligenceAgent.java` / `PlanningIntelligenceAgentTest.java`.

```java
// A new agent: @Component extends AbstractAgent, implement key()/displayName()/supportsPortfolio()/gather().
public interface Agent {
  String key(); String displayName(); boolean supportsPortfolio();
  GatherResult gather(AgentRunContext ctx);   // DETERMINISTIC — no LLM
}
record GatherResult(JsonNode dataSnapshot, List<AgentFindingDraft> candidates)   // snapshot drives change-detection
record AgentFindingDraft(String findingType, String subjectRef, Severity severity, double confidence,
    String confidenceBasis, String title, String whatHappened, String whyItHappened,
    String businessImpact, String recommendedAction, List<EvidenceRef> evidence,
    Map<String,List<UUID>> stakeholders, Instant validUntil)
enum Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }   // ordinal = rank
record EvidenceRef(String type, String label, String value, String entityType, UUID entityId, String linkUrl)
    // helpers: EvidenceRef.metric(label,value), EvidenceRef.entity(label,value,entityType,entityId,linkUrl)
record AgentRunContext(UUID projectId, boolean portfolio, String triggerType, String triggerRef,
    boolean force, UUID pipelineRunId, UUID requestedBy, Instant now)
// Cross-agent memory (read-only) via AbstractAgent's protected `runtime`:
//   runtime.memory().activeFindings(projectId, Set.of("capacity_utilisation"), Severity.MEDIUM)
```
Rules: `gather()` never throws on sparse data (return empty `GatherResult`); every draft field is real templated prose with the actual numbers (it is the LLM-down fallback); set `validUntil = ctx.now().plus(Duration.ofDays(7))` (24h for executive/portfolio briefs); confidence names its statistic in `confidenceBasis`; inject domain services + `ObjectMapper`; build the snapshot with stable ordering + rounded doubles.

---

## 6. Plan-vs-code corrections (the plan text was WRONG on these — the code is right)

- **`ADMIN_MASTER.WRITE` does NOT exist.** Only `ADMIN_MASTER.READ` (PermissionCatalog.java:122) and `ADMIN_MASTER.UPDATE` (:123). Use those for admin endpoints. (`AI.READ`/`AI.WRITE` at 104/145 are correct.)
- **Liquibase naming is numeric `NNN-<name>.yaml`** (latest was `117`), NOT `changeset-YYYY-MM-*.xml`. We used `118-`/`119-`.
- **Provider request field is `responseFormat`** (a `JsonNode` wrapped as `{type:"json_schema", json_schema:{name,strict:true,schema}}`), not `responseSchema`. Call `openAiCompatibleProvider.chat(config, chatReq)`.
- **`ScheduleHealthService.calculateHealth(scheduleResultId)`** takes a schedule-result id, not a projectId — resolve the latest schedule result first.
- **Monte Carlo entry = `MonteCarloService.runSimulation(projectId, req)`** (persisted); `MonteCarloEngine` is the pure compute engine — don't call it directly.
- **EMV = `RiskService.calculateRiskExposure(projectId)`** (returns BigDecimal), NOT `RiskExposureService` (that only recalcs exposure dates/costs).
- **`EvmServiceHelper` is package-private** — unreachable from the agent package; use `EvmService.computeEvmSnapshot(projectId)`.
- **Lease idiom** (no wrapper): `if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) return;` — see `DprApprovalSlaEscalationJob`.

---

## 7. Repo gotchas (these will waste hours if you don't know them)

- **Stale M2 jars:** `mvn -pl bipros-api spring-boot:run` uses stale `~/.m2` jars for sibling modules, so new routes 404. After editing `bipros-ai` (or `bipros-common`), run `mvn -pl <module> install` BEFORE running the app. `-am` with `spring-boot:run` does NOT work.
- **Local boot needs ClickHouse:** `spring-boot:run` dies at startup if ClickHouse (:8123) is down (`ClickHouseDataSourceConfig` bean is unconditional). Postgres/Hibernate `ddl-auto` run before the failure, so schema still creates.
- **`ddl-auto: update` is additive only** in dev (Liquibase disabled). It never drops columns / never removes NOT NULL. If you delete an entity field, drop the column manually or restart with `DDL_AUTO=create-drop`. Prod profile uses `ddl-auto: validate` + Liquibase — the changelogs are the source of truth there, so keep entity ↔ Liquibase column types in sync (`double`→`DOUBLE`, `byte[]`→`BYTEA`, JSON→`JSONB`, `Instant`→`TIMESTAMP`).
- **AI narration needs `BIPROS_AI_KEK`** (base64 KEK env var) to decrypt the stored LLM provider key; without it narration returns empty and agents fall back to templated prose (fine for a demo).
- **Postgres nullable JPQL param cast:** `(:p is null or …)` filters fail at runtime on Postgres; prefer derived queries with explicit `...IsNull...` variants (as done in the repos), or `cast(:p as ...)`. Mockito repo tests don't catch this — run against a real DB.
- **pnpm dev-server corepack bug:** `pnpm build`/`pnpm dev` can crash with `ERR_VM_DYNAMIC_IMPORT_CALLBACK_MISSING` on node 20.11.0; run `node node_modules/next/dist/bin/next build` (or use node 20.20.0).
- **This is Next.js 16** — read `frontend/node_modules/next/dist/docs/` before writing Next-specific code.
- **Subagent file corruption:** if a file-writing subagent dies "Connection closed mid-response", it may leave partial files or leak `</content>`/`</invoke>` tags — grep for them and strip before compiling.
- **Currency is relabel-only.** Portfolio/global screens must NOT call `useProjectCurrency()` (it throws outside a project provider). Backend emits raw numbers; project pages format via `useProjectCurrency()`.

---

## 8. Verification quick reference

```bash
# Backend
cd backend && mvn -pl bipros-common install -DskipTests        # after touching bipros-common
cd backend && mvn -pl bipros-ai test-compile                    # compile check
cd backend && mvn -pl bipros-ai test -Dtest='*AgentTest,...'    # unit tests
cd backend && mvn -pl bipros-ai install -DskipTests             # publish jar so the app sees new classes
cd backend && mvn clean install                                 # full build (stale-M2 safety) before done

# Run the stack
docker compose up -d                                            # Postgres + Redis (+ start ClickHouse + MinIO)
export BIPROS_AI_KEK=...                                         # base64 KEK for narration
cd backend && mvn spring-boot:run -pl bipros-ai... (actually -pl bipros-api)   # :8080
cd frontend && node node_modules/next/dist/bin/next dev         # :3000
./scripts/demo-agents.sh                                        # smoke the agents

# Frontend build
cd frontend && node node_modules/next/dist/bin/next build
```

Admin seed user on first boot: `admin` / `admin123`.

---

## 9. Key reuse pointers (files to clone from)

- `backend/bipros-ai/.../insights/InsightsGenerator.java` — pattern `AgentNarrator` was cloned from.
- `backend/bipros-ai/.../orchestrator/AiOrchestrator.java` — supervisor reuse + SSE idiom (`handle(...)`).
- `backend/bipros-ai/.../chat/ChatController.java` — `chatStream` SSE (`Flux<ServerSentEvent<String>>`) + `@aiAccess` + `resolveConfig()`.
- `backend/bipros-common/.../notification/NotificationService.java` — in-app channel + `existsSince` dedup.
- `backend/bipros-api/.../scheduling/DprApprovalSlaEscalationJob.java` — `@Scheduled` + `ScheduledJobLease.tryAcquire` template.
- `backend/bipros-dbs/.../service/recompute/` + `config/DbsRecomputeConfig.java` — bounded async executor pattern.
- `frontend/src/lib/api/aiApi.ts` — SSE fetch-stream parsing (Bearer). `agentApi.ts` already clones it.
- `frontend/src/components/hub/mission-control/primitives/Sparkline.tsx` — SVG animation idiom.

---

*Persisted memory (`~/.claude/projects/.../memory/proj_multi_agent_ai_platform.md`) has a compressed version of §6. Update it as Phase 2 lands.*
