# Role-Based Profiles & Role-Aware Global AI

**Date:** 2026-05-08
**Status:** Draft — pending implementation plan
**Scope:** Option B (full breadth, current data only)

## 1. Problem & Goal

Construction project staff at different levels ask different questions of the same data. A Site Manager asks *"which crew under-produced today?"*; a PM asks *"is our labour CPI eroding?"*; a QC Manager asks *"are NCRs concentrating on one operator?"* The current Global AI sees the same 40+ tool registry for every user, so it often picks tools that don't match the user's role-shaped intent and answers in a generic voice.

Two problems to solve in one iteration:

1. **Five user roles missing or under-specified**: BIM/Data Coordinator, QC Manager, Project Engineer, Site Manager (currently only `SITE_ENGINEER` exists, treated as junior), and a refined Project Manager.
2. **Global AI is role-blind in practice**: the role string is interpolated into the system prompt, but tool selection is unfiltered and the prompt does not encode role-specific KPI focus or persona.

Goal: every user sees an AI that defaults to the questions and KPIs their role cares about, using only data the system already captures.

## 2. Non-Goals

- New entities/tables for KPIs the system doesn't yet capture (calibration logs, cube test results, BBS reconciliation, gradation reports, asphalt temperature, weld certification expiry). These are deferred to a follow-up.
- Migrating existing `hasRole(...)` checks to permission-based authority checks. We extend, we don't migrate.
- Reworking the AI streaming/UI envelope.

## 3. Decisions Made During Brainstorming

| # | Decision |
|---|----------|
| Q1 | Scope: Option B — roles + permissions + AI tools answerable by current data |
| Q2 | RBAC: dual-write to both legacy `Role` (DataSeeder) and modern `Profile` (ProfileSeeder) |
| Q3 | Naming: keep `SITE_ENGINEER` as junior; add new `SITE_MANAGER` (above) and `PROJECT_ENGINEER` (design-side engineer); add `QC_MANAGER` and `BIM_DATA_COORDINATOR` |
| Q4 | AI mechanism: profile-driven role source; `allowedRoles` tag on tools with registry filtering; role-specific persona block appended to system prompt |
| Q5 | Tool set: 15 new tools across the 5 roles (4 SM + 3 PE + 3 QC + 3 PM + 2 BIM); existing tools tagged with `allowedRoles`; missing-data tools ship with graceful "data not yet captured" responses (option **b**), not silent skips |

## 4. Architecture Overview

Three logical layers, each with a single responsibility:

1. **Identity layer** — defines *who* a user is. Adds 4 new `Role` entries and 5 new/refined `Profile` entries (each profile carries its own permission set). Lives in `bipros-security` (entities) and `bipros-api/config` (seeders).
2. **AI capability layer** — defines *what* the AI can do for each identity. Extends the `Tool` interface with `allowedRoles()`, refactors `ToolRegistry` to filter per request, introduces a `RolePersonaProvider` that returns a focus block per role. Lives entirely in `bipros-ai`.
3. **AI tool layer** — defines *which questions* the AI can answer. Adds 15 new tools, each owning one KPI bucket, each calling existing repositories/ClickHouse views. Tagged with `allowedRoles`. Lives in `bipros-ai/tool/`.

These layers are deliberately decoupled: a future role addition is a seeder change + persona block; a future tool addition is one new file + a tag; neither forces a change to the other.

```
                       ┌──────────────────────────┐
   JWT (sub, profile)→ │   AiContextResolver      │ → AiContext{role, profile, scopedProjectIds}
                       └────────────┬─────────────┘
                                    ▼
            ┌────────────────────────────────────────────┐
            │           AiOrchestrator (ReAct)           │
            │  ┌───────────┐                             │
            │  │ persona   │←  RolePersonaProvider       │
            │  │ block     │                             │
            │  └───────────┘                             │
            │  ┌──────────────┐                          │
            │  │ filtered     │←  ToolRegistry           │
            │  │ tool list    │   .toolsForRole(role)    │
            │  └──────────────┘                          │
            └────────────────┬───────────────────────────┘
                             ▼ tool calls
                ┌────────────────────────────┐
                │   New + existing tools     │ → JPA / ClickHouse
                └────────────────────────────┘
```

## 5. Identity Layer

### 5.1 Roles (legacy `Role` table)

Add to `DataSeeder.java`:

| Role | Purpose |
|---|---|
| `BIM_DATA_COORDINATOR` | Data integrity / model linkage steward |
| `QC_MANAGER` | Process adherence, NCR ownership |
| `PROJECT_ENGINEER` | Design ↔ execution bridge, technical sign-off |
| `SITE_MANAGER` | Daily execution, crew/machine deployment |

`SITE_ENGINEER` stays. `PROJECT_MANAGER`, `ADMIN`, etc. unchanged.

### 5.2 Profiles (modern `Profile` + permissions)

Add 4 new profiles + refine PROJECT_MANAGER if needed. Each profile maps to a set of permission codes (existing scheme like `PROJECT.READ`, `COST.UPDATE`, `AI.READ`). The starting permission set per profile is derived by walking the profile-relevant controllers; below is the design intent — exact codes finalised during implementation against `PermissionCatalog`:

| Profile | Read | Write | Distinctive |
|---|---|---|---|
| `BIM_DATA_COORDINATOR` | PROJECT, ACTIVITY, DPR, RESOURCE, DOCUMENT, AI | DOCUMENT, UDF | DPR.AUDIT (new), DATA_QUALITY.READ (new) |
| `QC_MANAGER` | PROJECT, ACTIVITY, DPR, RESOURCE, RISK, AI | RISK, DPR.QC_ANNOTATE (new) | NCR.* (new namespace) |
| `PROJECT_ENGINEER` | PROJECT, ACTIVITY, DPR, RESOURCE, COST(read), AI | ACTIVITY, DPR | YIELD_VARIANCE.READ (new) |
| `SITE_MANAGER` | PROJECT, ACTIVITY, DPR, RESOURCE, AI | DPR, RESOURCE.DEPLOYMENT | LABOUR_RETURN.WRITE |
| `PROJECT_MANAGER` (refined) | + COST.READ ensured, EVM.READ ensured, AI.WRITE | unchanged | unchanged |

New permission codes are added to `PermissionCatalog` as needed. No code is removed.

### 5.3 Mapping role → profile → AI persona

We standardise on **profile name** as the key the AI uses. `AiContextResolver` is updated to resolve the profile from the user, falling back to legacy role only if no profile is assigned (preserves current admin-seeded users until they're migrated).

## 6. AI Capability Layer

### 6.1 `Tool` interface extension

```java
public interface Tool {
    String name();
    String description();
    JsonNode inputSchema();
    JsonNode execute(JsonNode input, AiContext ctx);

    /** Profiles allowed to invoke this tool. Empty set = visible to all. */
    default Set<String> allowedRoles() { return Set.of(); }
}
```

The `allowedRoles()` set holds **profile names** (matches Q4 decision: profile-driven). `ADMIN` is implicitly allowed everywhere — handled in registry, not in each tool.

### 6.2 `ToolRegistry` filtering

```java
public List<Tool> toolsForProfile(String profileName) {
    if ("ADMIN".equals(profileName)) return allTools;
    return allTools.stream()
        .filter(t -> t.allowedRoles().isEmpty()
                  || t.allowedRoles().contains(profileName))
        .toList();
}
```

Orchestrator calls `toolsForProfile(ctx.profile())` once per turn and passes the filtered list to the LLM. **Per-call enforcement also added**: if the LLM somehow calls a disallowed tool (model error), `executeTool` returns a clear "tool not available for your role" error rather than running it. Defense in depth.

### 6.3 `RolePersonaProvider`

A small immutable Spring bean that returns a persona block per profile:

```java
public record RolePersona(
    String headline,           // "You are assisting a Site Manager."
    List<String> primaryKpis,  // ["Labour utilization %", "Machine idle %", "Wastage %"]
    List<String> preferTools,  // ["analyze_labour_utilization", ...]
    String framingHint         // "Frame answers as today's wins/losses."
) {}
```

`AiOrchestrator.buildSystemPrompt()` appends the persona block after the existing project-scope block. When no persona is configured (e.g., ADMIN, or a user with no profile), the existing generic prompt is used — no regression.

## 7. AI Tool Layer

15 new tools (4 SM + 3 PE + 3 QC + 3 PM + 2 BIM), each in its own file under `bipros-ai/src/main/java/com/bipros/ai/tool/`. Naming follows existing convention (`snake_case` tool name, `*Tool` class).

For each tool, the spec records: name, role(s), one-sentence purpose, primary data source, gracefully-degraded response when data is sparse/missing. Detailed input schemas are deferred to the implementation plan.

### 7.1 Site Manager (4 new)

| Tool | Purpose | Source |
|---|---|---|
| `analyze_labour_utilization` | Actual man-hours vs planned/paid by crew for a date range | DPR + LabourReturn |
| `analyze_machine_idle_time` | List equipment with idle hours over threshold; surface downtime reasons where logged | EquipmentLog |
| `analyze_material_wastage` | Wastage % per location/material from reconciliation entries | MaterialReconciliation |
| `check_stockpile_vs_plan` | Current stock vs lookahead-window planned demand | Material stock + schedule lookahead |

### 7.2 Project Engineer (3 new)

| Tool | Purpose | Source |
|---|---|---|
| `analyze_productivity_factor` | Output per man-hour vs activity norm, by crew | DPR `qty_executed` + Activity norms |
| `analyze_yield_variance` | Material consumed vs design (BOQ) quantity by activity | Material consumption + BOQ |
| `analyze_equipment_cycle_time` | Cycle time per equipment pair where logs support it; "data not yet captured" otherwise | EquipmentLog |

### 7.3 QC Manager (3 new — degraded responses likely)

| Tool | Purpose | Source / Fallback |
|---|---|---|
| `analyze_ncr_trends` | NCRs/quality issues by crew or source | RiskRegister (proxy) — if no NCR entity, returns "NCR tracking not yet captured; closest available is the Risk Register" |
| `audit_traceability` | Material lot ↔ operator ↔ DPR location linkage | DPR + LabourReturn + MaterialIssue |
| `analyze_quality_data_gaps` | Flag missing test/calibration entries on activities marked QC-required | DPR + Activity flags |

### 7.4 Project Manager (3 new — most coverage already exists)

| Tool | Purpose | Source |
|---|---|---|
| `analyze_labour_cost_per_unit` | $ per unit installed vs budget unit rate | LabourReturn cost + DPR qty + Cost |
| `analyze_material_burn_rate` | Burn rate of high-value materials vs procured; highlight NTP-exhaustion risk | Material consumption + procurement |
| `analyze_equipment_utilization_cost` | Utilization % and $/hr for owned vs rented equipment | EquipmentLog + Cost |

### 7.5 BIM/Data Coordinator (2 new)

| Tool | Purpose | Source |
|---|---|---|
| `audit_dpr_data_quality` | Per-project DPR completeness: missing fields, late entries, missing breakdown codes | DPR |
| `report_data_lag` | Site event timestamp → DPR system entry timestamp distribution | DPR audit fields |

### 7.6 Existing tools — `allowedRoles` tagging

Done in-place; no code logic changes:

- `portfolio_kpi`, `analyze_cost`, `forecast_completion`, `analyze_schedule`, `analyze_risk` → `PROJECT_MANAGER`, `EXECUTIVE`, `PMO` (and ADMIN implicitly)
- `query_dpr`, `get_dpr_details`, `query_daily_outputs` → all 5 new profiles + existing site-facing profiles
- `list_projects` → all profiles
- Tools with no clear role match stay untagged (visible to all)

### 7.7 Graceful degradation contract

When a tool's underlying data isn't populated for the given scope, it MUST return a structured response of the form:

```json
{
  "status": "data_unavailable",
  "reason": "EquipmentLog entries do not yet capture cycle start/end timestamps for project X.",
  "what_would_be_needed": "Capture per-cycle start_at and end_at on EquipmentLog, or a CycleEvent entity.",
  "closest_available": "Equipment idle-time report via analyze_machine_idle_time"
}
```

The orchestrator surfaces this verbatim to the user so the gap is visible. The tool MUST NOT silently return an empty array.

## 8. Frontend Impact

Minimal in this iteration:

- `RoleGuard` already gates by role; works unchanged for new roles. Optional follow-up: gate by profile.
- `AiChatPanel` tool-progress label map gains friendly labels for the 12 new tools.
- No new screens.

## 9. Testing Strategy

- **Identity layer**: existing seeder integration tests get new assertions for the 4 new roles and 5 profile records. Permission-set assertions: each new profile has the documented permission codes.
- **Capability layer**: unit test `ToolRegistry.toolsForProfile()` for each profile; assert ADMIN sees all, an unprofiled user sees only untagged tools, a `SITE_MANAGER` sees the 8-tool list above.
- **Tool layer**: each new tool gets a unit test for happy path + a `data_unavailable` response test. Where ClickHouse views are involved, repository-level tests use the existing test slice.
- **End-to-end**: one integration test per role: stub a JWT for that profile, send a representative chat prompt, assert the LLM was offered the expected filtered tool list. (We don't assert which tool the LLM picks — that's flaky.)

## 10. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Permission codes break existing users | Only **adding** codes / profiles. Existing users with profiles other than the 5 in scope are untouched. |
| Tool filtering breaks ADMIN debugging | ADMIN is implicitly all-tools at registry level; explicit test. |
| Persona prompt drift makes LLM less helpful | Persona block is additive — base prompt is unchanged, regression visible if generic queries get worse. We keep generic chat working for ADMIN and unprofiled users. |
| `data_unavailable` responses become too common | If >30% of role-tagged tools return `data_unavailable` for a real project, that's a signal the project hasn't been instrumented yet — the AI's response itself becomes the operational ask. Acceptable. |
| Field rename in legacy Role enum elsewhere in code | Survey turned up no enum — `Role.name` is a string column. Adding rows is non-breaking. |

## 11. Out-of-Scope Follow-ups (named for the next iteration)

- Calibration log entity + `analyze_calibration_drift` tool
- Cube test results entity + `analyze_concrete_strength_trends` tool
- BBS (Bar Bending Schedule) reconciliation + `analyze_rebar_yield` tool
- Welder/operator certification expiry + `audit_certifications` tool
- 4D model linkage + heat-map tools for BIM Coordinator
- Profile-based `<RoleGuard>` on the frontend

## 12. Open Questions for Implementation

These do not block plan-writing but will be resolved during the plan:

1. Exact permission-code namespacing for new permissions (`NCR.READ` vs `QUALITY.NCR.READ` vs reuse of existing).
2. Whether `audit_dpr_data_quality` should run on ClickHouse views or directly on JPA — depends on which gives clearer "missing field" semantics.
3. Whether `analyze_material_burn_rate` should join procurement orders (if available) or be inferred from issuing logs alone.

---

**End of design.**
