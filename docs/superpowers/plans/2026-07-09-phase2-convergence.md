# Phase 2 Convergence — Multi-Agent AI Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the Multi-Agent AI Platform — supervisor mode (LLM chats that run agents as tools), missing REST endpoints, event wiring, frontend SSE swaps + admin pages, and full test/verification.

**Architecture:** Supervisor mode reuses the existing `AiOrchestrator.handle(...)` ReAct loop by registering `run_agent`/`read_agent_findings` as `Tool` beans (auto-registered by `ToolRegistry`). New REST endpoints extend `AgentController`/new `AgentAdminController`. Two new minimal pipelines (`DOCUMENT_REACTIVE`, `GIS_REACTIVE`) wire the document/GIS events. Frontend swaps seeded/polling data for SSE streams (cloning the `parseSse` + `useAgentStream` patterns).

**Tech Stack:** Java 23 / Spring Boot 3.5, JUnit 5 + Mockito + Testcontainers, Next.js 16 / React 19 / TanStack Query / Vitest / Playwright.

**Branch:** `khasab-demo-ready-2026-05-24-temp` (current). Commit per task.

**Source:** `docs/AGENT-PLATFORM-HANDOFF.md` (Phase 2, §4) + `~/.claude/plans/prompt-transform-the-unified-moon.md`.

---

## Verified contracts & corrections

All Phase 2 contracts were verified against the actual code. Key signatures (verbatim):

- `Tool`: `name()`, `description()`, `inputSchema()→JsonNode`, `execute(JsonNode input, AiContext ctx)→ToolResult`, `default isReadOnly()→true`, `default allowedRoles()→Set.of()`.
- `ToolResult` (record `success, summary, data, error`): `ok(summary, data)`, `ok(summary)`, `error(err)`, `table(summary, ArrayNode rows, String[] columns)`.
- `ProjectScopedTool`: abstract `doExecute(JsonNode, AiContext)`; **scope check skipped when `ctx.projectId()==null`** (portfolio). `RunAgentTool` extends `Tool` directly and enforces scoping inline.
- `AiContext` (record): `userId()`, `projectId()`, `module()`, `role()`, `profile()`, `scopedProjectIds()`, `hdsVersionIds()`. Build via `AiContextResolver.resolve(projectId, "ai")`. **Accessor is `userId()` not `currentUserId()`.**
- `ToolRegistry(Collection<Tool>)` → injects all `Tool` beans; `toolsForProfile(profile, role)`, `isAllowed(name, profile, role)`, `get(name)`.
- `AiOrchestrator.handle(String userMessage, String imageUrl, List<LlmProvider.Message> history, AiContext ctx, LlmProvider provider, LlmProviderConfig config)→Flux<ChatEvent>`. `ChatEvent` = nested record `AiOrchestrator.ChatEvent(String event, Map<String,Object> data)`.
- `ChatController.chatStream`: `POST /v1/ai/chat/stream`, `@PreAuthorize("@aiAccess.canChat(#request.projectId)")`, `Flux<ServerSentEvent<String>>`. `resolveConfig()` picks default `LlmProviderConfig` (`findByIsDefaultTrueAndIsActiveTrue().or(findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc).orElseThrow(...)`). `OpenAiCompatibleLlmProvider` is a single injected bean.
- `AgentRunService.runSingle(String agentKey, AgentRunContext ctx)→AgentRun`.
- `AgentRunContext.manual(UUID projectId, UUID requestedBy)`: portfolio=true when projectId==null, force=true.
- `AgentMemoryService.activeFindings(UUID projectId, Set<String> agentKeys, Severity minSeverity)→List<AgentFinding>`. Also `activeFindingsForProjects(Collection<UUID>)`.
- `AgentInvestigation` entity: `projectId, question, answer, runIdsJson(jsonb), tokensInput, tokensOutput, askedBy`. Repo `AgentInvestigationRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)`.
- `AgentPipelineRunner.run(String pipelineKey, UUID projectId, String triggerType, String triggerRef)→UUID` (param name `triggerType`; returns pipelineRunId, null if unknown key).
- `AgentPipelines`: 5 String constants (`DAILY_PROJECT_SWEEP`, `OPERATIONS_REACTIVE`, `SCHEDULE_REACTIVE`, `RISK_REACTIVE`, `PORTFOLIO_WEEKLY`).
- `AgentChannelConfig`: `channelKey, apiUrl, accountSid` (NOT `sid`), `authTokenCiphertext(byte[]), authTokenIv(byte[]), authTokenVersion(Integer)` (encrypted triple), `fromNumber, active`. Repo `findByChannelKey`, `findByActiveTrue`.
- `ApiKeyCipher` (package `com.bipros.ai.provider.crypto`): `encrypt(String)→EncryptedKey(iv, ciphertext, version)`, `decrypt(byte[] iv, byte[] ciphertext, int version)→String`.
- `AgentBudgetProperties` (`@ConfigurationProperties("bipros.agent.budget")`, `@Getter @Setter`): `perRunTokens, perProjectDailyTokens, globalDailyTokens, supervisorPerInvestigationTokens`. **yml-bound only, no DB backing** → admin endpoint is GET-only.
- `AgentBudgetUsage`: `projectId (GLOBAL_SCOPE=UUID(0,0)), usageDate, tokensReserved, tokensUsed, runCount`. Repo: `findByProjectIdAndUsageDate`, `lockByProjectIdAndUsageDate`.
- `PermissionCatalog` (bipros-security, record-based): `ADMIN_MASTER.READ` (L122), `ADMIN_MASTER.UPDATE` (L123); **`ADMIN_MASTER.WRITE` does NOT exist**. `AI.READ` (L104), `AI.WRITE` (L145).
- `AiAccessGuard` (`@Component("aiAccess")`): `canRead(UUID)`, `canWrite(UUID)`, `canChat(UUID)`.
- `NotificationStreamController`: `GET /v1/notifications/stream`, `@PreAuthorize("isAuthenticated()")`, `Flux<ServerSentEvent<String>>`. `NotificationSseHub.publish(UUID userId, JsonNode payload)`.
- `ScheduledJobLeaseRepository.tryAcquire(String name, Instant until, Instant now, String owner)→int` (1=acquired, 0=failed).
- `AgentController` existing endpoints: GET agents, POST agents/{key}/run, GET agent-runs, GET agent-runs/{id}, GET agent-findings, POST findings/{id}/acknowledge, POST findings/{id}/resolve. **No investigate/pipeline-run/portfolio-activity yet.** `PipelineRunAcceptedResponse(UUID pipelineRunId)` DTO already exists.
- `AgentTriggerListener` handler pattern: `@TransactionalEventListener(phase = AFTER_COMMIT) void onX(Event e) { trigger(AgentPipelines.X, e.projectId(), "EventClassName"); }`. No handlers for DocumentUploadedEvent/GisSnapshotAnalyzedEvent.

Frontend:
- `agentApi.streamAgents(projectId, signal)` uses `parseSse(res, signal)` + `authHeaders()`. `agentApi.investigate(projectId, question, signal)` already calls `POST /v1/projects/{projectId}/agents/investigate` (frontend fully wired, waits gracefully).
- `useAgentStream`: SSE + exponential backoff (`min(15s, 1s*2^failures)`, `MAX_STREAM_FAILURES=4`) + polling fallback (10s disconnected, 45s connected).
- `InvestigatePanel` expects events: `token`(delta/token), `gathering`, `narrating`, `finding`(title/severity/findingType/findingId), `done`/`final_answer`/`run_finished`(text/answer), `error`(message).
- `ActivityTicker` uses `SEED_EVENTS`; `TickerEvent` = `{id, projectCode, actor, verb, subject, href, agoLabel}` (no ISO timestamp).
- `NotificationBell` polls `notificationApi.unreadCount` every 45s; `invalidateBoth` helper is cache-busting seam.
- Admin pages: 31 existing under `src/app/(app)/admin/`. **No channels/budgets.** Clone `skills/page.tsx` pattern (`useQuery` + `invalidateQueries` + plain `<form onSubmit>`, `VirtualDataTable`).
- `TOOL_PROGRESS_LABELS` duplicated in `toolLabels.ts` (canonical) AND `AiChatPanel.tsx` (in-file copy, imported by a test). `run_agent`/`read_agent_findings` absent from both.
- `package.json` has no `test`/`vitest` script — must use `pnpm vitest run` or add script.
- `useProjectCurrency()` throws outside project provider; use `useProjectCurrencyOptional()` on portfolio/admin surfaces.

---

## Section 4a — Supervisor mode

**Files:**
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/agent/supervisor/RunAgentTool.java`
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/agent/supervisor/ReadAgentFindingsTool.java`
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/agent/supervisor/SupervisorService.java`
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/agent/api/InvestigateController.java`
- Test: `backend/bipros-ai/src/test/java/com/bipros/ai/agent/supervisor/RunAgentToolTest.java`, `ReadAgentFindingsToolTest.java`, `SupervisorServiceTest.java`, `InvestigateControllerTest.java`

### Task 4a.1: RunAgentTool
- [ ] Write failing test `RunAgentToolTest`: mock `AgentRunService`, assert `runSingle` called with `AgentRunContext.manual(projectId, userId)`, assert `ToolResult.ok` shape, assert `isReadOnly()=false`.
- [ ] Run test → verify fail.
- [ ] Implement `RunAgentTool` (`@Component implements Tool`): `name()="run_agent"`, `isReadOnly()=false`, `inputSchema()`={agentKey:string, projectId?:string}. `execute`: scope-check inline (if projectId!=null and !"ADMIN".equals(ctx.role()) and !ctx.scopedProjectIds().contains(projectId) → throw AccessDeniedException), build `AgentRunContext.manual(projectId, ctx.userId())`, call `agentRunService.runSingle(agentKey, ctx)`, map returned `AgentRun`+findings to `ToolResult.ok(summary, findingsJson)`.
- [ ] Run test → verify pass.
- [ ] Commit.

### Task 4a.2: ReadAgentFindingsTool
- [ ] Write failing test: mock `AgentMemoryService`, assert filter params, assert `ToolResult.table` shape.
- [ ] Run → fail.
- [ ] Implement: `name()="read_agent_findings"`, read-only, `inputSchema()`={agentKey?:string, severity?:string}. `execute`: parse optional agentKey (→Set of one or empty) + severity (→`Severity` enum or null→INFO), call `agentMemoryService.activeFindings(ctx.projectId(), keys, minSeverity)`, return `ToolResult.table(summary, rows, columns)`.
- [ ] Run → pass.
- [ ] Commit.

### Task 4a.3: SupervisorService
- [ ] Write failing test: mock `AiOrchestrator.handle` → `Flux.just(events)`, mock `AgentInvestigationRepository`, assert persona addendum contains agent keys, assert persistence.
- [ ] Run → fail.
- [ ] Implement `SupervisorService.investigate(question, ctx)`: prepend persona addendum (list 11 agents via `AgentRegistry.all()` + delegation guidance), call `aiOrchestrator.handle(supervisorMessage, null, history, ctx, llmProvider, resolveConfig())`, collect `Flux<ChatEvent>`. After completion persist `AgentInvestigation` (question, answer=accumulated tokens, runIds from tool-call events, tokensInput/Output, askedBy=ctx.userId()). Return `Flux<ChatEvent>`.
- [ ] Run → pass.
- [ ] Commit.

### Task 4a.4: InvestigateController
- [ ] Write failing test (`@WebMvcTest`): mock `SupervisorService`, assert 200 + `text/event-stream`.
- [ ] Run → fail.
- [ ] Implement: `POST /v1/projects/{projectId}/agents/investigate` produces `text/event-stream`, `@PreAuthorize("@aiAccess.canWrite(#projectId)")`, body `{question}`. Build `AiContext` via `aiContextResolver.resolve(projectId, "ai")`, call `supervisorService.investigate(question, ctx)`, map `Flux<ChatEvent>`→`Flux<ServerSentEvent<String>>` (clone `ChatController.chatStream` SSE mapping). Add `done` terminal event.
- [ ] Run → pass.
- [ ] Commit.

---

## Section 4b — Missing REST endpoints

**Files:**
- Modify: `backend/bipros-ai/.../agent/api/AgentController.java` (+2 endpoints)
- Create: `backend/bipros-ai/.../agent/api/AgentAdminController.java`
- Modify: `AgentFindingRepository.java`, `AgentRunRepository.java`, `AgentBudgetUsageRepository.java` (+queries)
- Test: extend `AgentControllerTest`, `AgentAdminControllerTest`

### Task 4b.1: Pipeline-run endpoint
- [ ] Test: mock `AgentPipelineRunner`, assert 202 + pipelineRunId; null return → 404.
- [ ] Implement `POST /v1/projects/{projectId}/agents/pipelines/{key}/run` `@aiAccess.canWrite`, returns 202 `ApiResponse<PipelineRunAcceptedResponse>` calling `agentPipelineRunner.run(key, projectId, "MANUAL", "manual:"+user)`. Null → `BusinessRuleException("PIPELINE_UNKNOWN",...)`.
- [ ] Pass + commit.

### Task 4b.2: Portfolio agent-activity endpoint
- [ ] Add repo queries: `AgentRunRepository.findFirst20ByOrderByStartedAtDesc(Pageable)`, `AgentFindingRepository.findFirst20ByStatusOrderByLastSeenAtDesc(FindingStatus, Pageable)`.
- [ ] Test: mock repos, assert merge ordering + DTO shape.
- [ ] Implement `GET /v1/portfolio/agent-activity?limit=20` `@aiAccess.canRead(null)`. Merge+map to DTO `{type, projectId, agentKey, status/severity, startedAt/lastSeenAt, title?}`.
- [ ] Pass + commit.

### Task 4b.3: Admin channels CRUD
- [ ] Test: mock repo + `ApiKeyCipher`, assert encrypt on write, assert GET masks token.
- [ ] Implement `AgentAdminController @RequestMapping("/v1/admin/agent-channels")`: `GET` (`ADMIN_MASTER.READ`) → list → DTO (apiUrl, accountSid, fromNumber, active, channelKey, hasAuthToken). `PUT` (`ADMIN_MASTER.UPDATE`) body `{channelKey, apiUrl, accountSid, authToken?(plaintext), fromNumber, active}` → find/new, set fields, if authToken present → `apiKeyCipher.encrypt(token)`→store triple, save.
- [ ] Pass + commit.

### Task 4b.4: Admin budgets GET
- [ ] Add `AgentBudgetUsageRepository.findByUsageDate(LocalDate)`.
- [ ] Test: mock `AgentBudgetProperties`+repo, assert limits+usage shape.
- [ ] Implement `GET /v1/admin/agent-budgets?date=` (`ADMIN_MASTER.READ`) → `{limits: {...from AgentBudgetProperties}, usage: List<{projectId, usageDate, tokensReserved, tokensUsed, runCount}>}`.
- [ ] Pass + commit.

---

## Section 4c — Wire the two new events

**Files:**
- Modify: `bipros-document/.../DocumentService.java` (+`ApplicationEventPublisher`)
- Modify: `bipros-gis/.../ProgressAnalyzerService.java` (+publisher)
- Modify: `AgentPipelines.java` (+2 constants), pipeline registry
- Modify: `AgentTriggerListener.java` (+2 handlers)
- Test: extend `AgentTriggerListenerTest`

### Task 4c.1: New pipeline constants
- [ ] Test: assert constants + correct stages.
- [ ] Implement `DOCUMENT_REACTIVE = stages(Set.of("document_intelligence"), Set.of("notification"))`, `GIS_REACTIVE = stages(Set.of("gis_intelligence"), Set.of("notification"))`. Register in pipeline registry (clone existing pattern).
- [ ] Pass + commit.

### Task 4c.2: Trigger handlers
- [ ] Test: mock `AgentTriggerCoalescer`, publish events, assert `upsert` called with correct pipeline+projectId.
- [ ] Implement `onDocumentUploaded`→`trigger(DOCUMENT_REACTIVE, e.projectId(), "DocumentUploadedEvent")`, `onGisSnapshotAnalyzed`→`trigger(GIS_REACTIVE, e.projectId(), "GisSnapshotAnalyzedEvent")`.
- [ ] Pass + commit.

### Task 4c.3: Wire publishers
- [ ] Test: mock `ApplicationEventPublisher`, call create/upload, assert `publishEvent` called.
- [ ] Implement: add `ApplicationEventPublisher` to `DocumentService`, publish `new DocumentUploadedEvent(projectId, saved.getId())` after create/upload. Add to `ProgressAnalyzerService`, publish `new GisSnapshotAnalyzedEvent(image.getProjectId(), snapshot.getId())` after snapshot save (L99).
- [ ] Pass + commit.

---

## Section 4d — Frontend SSE swaps + admin pages

**Files:**
- Create: `useNotificationStream.ts`, `channelApi.ts`, `budgetApi.ts`, `admin/channels/page.tsx`, `admin/budgets/page.tsx`
- Modify: `NotificationBell.tsx`, `ActivityTicker.tsx`, `toolLabels.ts` (+dedupe AiChatPanel), `package.json`, `agentApi.ts` (+portfolioActivity)
- Test: `useNotificationStream.test.ts`, `FindingCard.test.tsx`, `ConfidenceBadge.test.tsx`, `useAgentStream.test.ts`

### Task 4d.1: notificationApi.streamNotifications + useNotificationStream
- [ ] Test: mock fetch+ReadableStream, assert event parsing + cache mutation + fallback.
- [ ] Implement `notificationApi.streamNotifications(signal)` (clone `agentApi.streamAgents`, GET `/v1/notifications/stream`). Implement `useNotificationStream` (clone `useAgentStream` backoff+fallback, keyed to user). On event → `queryClient.setQueryData(["notifications-list"], prepend)` + bump unread.
- [ ] Pass + commit.

### Task 4d.2: NotificationBell integration
- [ ] Test: assert hook wired.
- [ ] Implement: call `useNotificationStream()` in `NotificationBell`, push events into cache, keep 45s poll fallback.
- [ ] Pass + commit.

### Task 4d.3: ActivityTicker live feed
- [ ] Test: assert react-query fetch + mapping + empty fallback.
- [ ] Implement `agentApi.portfolioActivity()`→`GET /v1/portfolio/agent-activity`. Swap `SEED_EVENTS` for `useQuery` (refetchInterval 30s) + map backend DTO→`TickerEvent` (format agoLabel, build href). Keep `SEED_EVENTS` as empty fallback.
- [ ] Pass + commit.

### Task 4d.4: toolLabels dedupe + new entries
- [ ] Test: assert `run_agent`/`read_agent_findings` labels resolve.
- [ ] Implement: add `run_agent: "Running agent"`, `read_agent_findings: "Reading findings"` to `toolLabels.ts`. Refactor `AiChatPanel.tsx` to re-export from `toolLabels.ts`; update test import.
- [ ] Pass + commit.

### Task 4d.5: channelApi + channels admin page
- [ ] Implement `channelApi.ts` (list/put). Clone `skills/page.tsx` for `admin/channels/page.tsx` (CRUD, plaintext token on PUT only).
- [ ] `pnpm lint` + commit.

### Task 4d.6: budgetApi + budgets admin page
- [ ] Implement `budgetApi.ts` (get). Clone skills pattern for `admin/budgets/page.tsx` (read-only).
- [ ] `pnpm lint` + commit.

### Task 4d.7: package.json test scripts
- [ ] Add `"test": "vitest run"`, `"test:watch": "vitest"`.
- [ ] Commit.

---

## Section 4e — Tests + verification

**Files:**
- Create: `backend/bipros-api/src/test/.../agent/AgentInvestigationIntegrationTest.java`
- Create: `frontend/src/components/ai/agents/__tests__/{FindingCard,ConfidenceBadge,useAgentStream}.test.tsx`
- Create: `frontend/e2e/ai-overview.spec.ts`

### Task 4e.1: Integration test (Testcontainers)
- [ ] Implement `AgentInvestigationIntegrationTest` (`@ActiveProfiles("test")`, Testcontainers PG17, Liquibase): publish `DprSubmittedEvent`→assert queue→drain→assert pipeline+agent SUCCEEDED (`@MockitoBean OpenAiCompatibleProvider`)→assert `agent_finding` row→assert SSE event. Test `POST .../investigate` returns SSE. Validates changelogs 118/119.
- [ ] Run: `mvn -pl bipros-api test -Dtest=AgentInvestigationIntegrationTest`.

### Task 4e.2: Frontend vitest
- [ ] Implement `FindingCard.test.tsx`, `ConfidenceBadge.test.tsx`, `useAgentStream.test.ts` (mock ReadableStream, assert events+reconnect+fallback).
- [ ] Run: `pnpm vitest run`.

### Task 4e.3: Playwright e2e
- [ ] Implement `e2e/ai-overview.spec.ts`: login→AI Overview→Run sweep→RUNNING feed→finding card.
- [ ] Run: `pnpm test:e2e`.

### Task 4e.4: Full build + demo
- [ ] `mvn -pl bipros-common install -DskipTests && mvn -pl bipros-ai install -DskipTests`
- [ ] `mvn clean install` (full, stale-M2 safety)
- [ ] `node node_modules/next/dist/bin/next build` (Next 16)
- [ ] `pnpm lint`
- [ ] Boot: `docker compose up -d` (+ClickHouse), `export BIPROS_AI_KEK=...`, `mvn -pl bipros-api spring-boot:run`, `node .../next dev`
- [ ] `./scripts/demo-agents.sh`

---

## Repo gotchas

- **Stale M2 jars:** run `mvn -pl <module> install` before `spring-boot:run` after editing bipros-ai/bipros-common.
- **ClickHouse required for local boot** (`ClickHouseDataSourceConfig` unconditional).
- **`BIPROS_AI_KEK`** env var needed for narration (else templated fallback).
- **Next.js 16** — read `frontend/node_modules/next/dist/docs/` before Next code. Use `node node_modules/next/dist/bin/next build` (corepack bug on node 20.11).
- **Currency** — portfolio/admin pages must NOT call `useProjectCurrency()`.
- **No `pnpm test` script** — add it (4d.7) or use `pnpm vitest run`.
- **Subagent corruption** — if a file-writing subagent dies mid-response, grep touched files for `</content>`/`</invoke>`/`</parameter>` and strip.

---

## Execution order

4a, 4b, 4c (independent backend) → 4d (needs 4b) → 4e (needs all). Start with 4a (supervisor mode) — highest value, all contracts ready.
