---
sidebar_position: 1
title: AI Assistant — Deep Dive
description: Technical reference for the conversational AI assistant, document-aware generation, MDX insights, agentic tools and cached insights
---

# AI Assistant — Deep Dive

## Overview

Bipros EPPM ships a conversational AI assistant that sits alongside the application as a slide-out side panel. Unlike the [AI & Predictions](../ai-predictions/) module — which focuses on numeric forecasts and schedule health scoring — the AI Assistant is a **chat-first, agentic** experience that can read project, activity, resource, cost and DPR data, render inline charts, and generate WBS structures and activity lists from uploaded project documents.

The assistant is available from any authenticated page via the **Bot** icon in the application header. When a project is in scope (the user is viewing a project page), the assistant automatically scopes its tools and questions to that project. On admin pages, the assistant runs in a **general scope** with portfolio-wide tooling.

## Actors & Roles

| Actor | Role |
|---|---|
| **Project Manager** | Asks scope-specific questions, generates WBS / activities from documents, reviews narrated insights |
| **Planner / Scheduler** | Uses document-aware generation to bootstrap a schedule, applies AI suggestions through deterministic safety nets |
| **Executive** | Asks portfolio-level questions, reviews cached insight panels |
| **Resource Manager** | Runs role-aware deployment searches and resource utilisation summaries |
| **System Administrator** | Configures LLM providers, monitors AI conversation history and tool calls |
| **System** | Pre-computes cached insights per domain, runs async generation jobs, executes ReAct tool loops |

## Core Concepts

### Conversational Assistant

The chat panel is a streaming, server-sent-events (SSE) interface. Each user message is appended to a **conversation** that retains:

- All prior user and assistant messages
- The full chain of tool calls and tool results the assistant produced
- The active **context** (project ID, activity ID, page route, or "general")
- Any uploaded images attached to the conversation

The assistant honours the conversation context across turns — if a follow-up question is ambiguous, prior turns are used to disambiguate.

### Smart Conversation Titles

When a conversation reaches a meaningful first exchange, the system auto-generates a short, human-readable title summarising the topic (for example, *"Critical path slippage in Phase 2"* rather than *"New chat"*). Titles are written back to the conversation record and shown in the **History** view.

### Agentic ReAct Loop

The assistant runs a **Reason–Act** loop on the server. Rather than answering directly from memory, it:

1. **Reasons** about the user's question against the available tool catalogue.
2. **Calls one or more tools** (e.g. `list_activities`, `analyze_schedule`, `find_resource_deployment`, `forecast_completion`) with structured arguments.
3. **Reads the tool results** and decides whether more tooling is needed.
4. **Returns a layperson-friendly answer** — narrating what it looked at, not the raw tool plumbing.

The user sees friendly progress labels ("Looking up projects", "Reading schedule health", "Checking resource deployment") while the loop runs, never the underlying tool names.

### Inline ECharts Visualisations

When numeric or comparative data is in the answer, the assistant emits a fenced ` ```chart ` block containing an ECharts spec. The chat renderer detects these blocks and replaces them with a fully interactive chart inline. Charts support:

- **Copy** the conversation snippet (text + chart spec) to the clipboard
- **Maximise** the chart to a full-width modal for closer inspection
- Export of the entire conversation to **CSV**, **XLSX** or **PDF**

### MDX-Narrated Insights

Insight panels embedded in the application (for example, on a project dashboard) use a different rendering path: the backend renders an **MDX document** that interleaves narrative prose with **server-built ECharts specs**. The frontend mounts the MDX, and chart components read their specs from named slots. This produces consistent, narrated insight cards without round-tripping a live LLM call on every page load.

### Cached AI Insights

Insight cards are **pre-computed and cached** per domain (project, schedule, cost, EVM, risk, resource, portfolio). The pipeline is:

1. A **per-domain collector** gathers a stable, deterministic snapshot of the relevant data.
2. A **data hash** is computed over that snapshot.
3. If the cache already holds an insight for the same hash, it is served immediately.
4. Otherwise, an **insight generator** invokes the LLM, the result is stored under that hash, and a generation lock prevents duplicate work.

Hash-based caching means the panel only re-generates when the underlying numbers actually change, not on every page visit.

### Document-Aware Generation

Project documents (specifications, scope statements, client RFPs) uploaded to a project's document library can be used to **ground** AI generation:

- **WBS generation** — the AI proposes a hierarchical work breakdown structure derived from the uploaded documents, using a map-reduce extractor for large files and a hierarchy reconstructor to ensure parent–child consistency.
- **Activity generation** — the AI proposes activities (with durations, predecessors and resource hints) under a chosen WBS branch, again grounded in the document content.

Both flows run as **async jobs**: the user kicks off generation, the job runs on a background worker, and the result is surfaced when ready. Job state is persisted so the user can navigate away and return. Extraction results are cached so the same document does not re-pay the extraction cost across runs.

### Deterministic Safety Nets

AI-generated WBS and activity payloads pass through deterministic validators **before** they can be applied to the schedule. The safety nets enforce:

- WBS code uniqueness and parent–child integrity
- Activity ID uniqueness within the project
- Relationship validity (no circular predecessors, valid lag values, end nodes have no successors of the wrong type)
- Calendar and date-range sanity (start ≤ finish, within project window)
- Apply-mode handling (insert / merge / replace) with collision detection

If any check fails, the apply step is blocked and the user is shown which items collided. The AI's draft is preserved so the user can adjust and re-apply.

### Agentic Tools

The AI's tool catalogue spans the product's bounded contexts. Tools are grouped under a **resource facade** and a **data graph catalogue** so the AI sees a curated, schema-described view of the data — not raw tables. Representative tools:

| Domain | Tool | Purpose |
|---|---|---|
| Projects | `list_projects` | Enumerate projects in scope |
| Activities | `list_activities`, `query_wbs`, `get_activity_full_context` | Read schedule structure |
| Activities | `query_relationships` | Inspect predecessor / successor logic |
| Resources | `list_activity_resources`, `summarize_activity_resources` | Resource assignments and rolled-up costs by type |
| Resources | `find_resource_deployment` | Role-aware capacity / deployment search |
| Schedule | `analyze_schedule`, `schedule_advanced` | Health metrics, critical path |
| Cost | `analyze_cost`, `cost_breakdown` | Variance, CPI, breakdown |
| EVM | `forecast_completion` | EAC / ETC projections |
| Risk | `analyze_risk` | Risk exposure summary |
| DPR | `query_dpr`, `compare_actual_vs_norm`, `weather_log` | Field reporting and norms |
| Portfolio | `portfolio_kpi` | Cross-project KPI roll-ups |
| Baseline | `baseline_compare` | Current vs baseline deltas |
| Calendar | `calendar_tool` | Working time and holiday lookups |
| Plan | `next_day_plan` | Tomorrow's planned activities |
| Catalog | `describe_schema`, `query_clickhouse` | Data graph inspection and analytical queries |

### Role-Aware Deployment Search

The `find_resource_deployment` tool understands resource **roles** (Supervisor, Operator, Foreman, etc.) and capacity-utilisation metrics. Asked *"who's available next week with a tower-crane qualification?"*, the assistant filters by role, qualification and overlapping deployment windows, then returns a narrated list. The companion `summarize_activity_resources` tool produces a roll-up — by resource type, by role, or by cost bucket — for a given activity or set of activities.

## Use Cases

### UC-AI-10: Ask a Project-Scoped Question

| Attribute | Value |
|---|---|
| **ID** | UC-AI-10 |
| **Name** | Ask a Project-Scoped Question |
| **Actor** | Project Manager |
| **Precondition** | User is viewing a project page; assistant panel is open |
| **Trigger** | User types a question and submits |

**Main Flow:**
1. User asks, for example, *"How is the critical path looking this week?"*
2. The assistant detects project scope from the active route.
3. The ReAct loop calls `analyze_schedule` and `query_wbs`.
4. Friendly progress labels are streamed to the panel.
5. The assistant returns a narrated answer with an inline ECharts float-distribution chart.
6. The conversation is persisted and a smart title is generated.

**Postcondition:** The conversation is saved and accessible from the **History** view.

### UC-AI-11: Generate WBS from a Document

| Attribute | Value |
|---|---|
| **ID** | UC-AI-11 |
| **Name** | Generate WBS from a Document |
| **Actor** | Planner / Scheduler |
| **Precondition** | A scoping document has been uploaded to the project document library |
| **Trigger** | User opens the WBS view and selects **Generate from Document** |

**Main Flow:**
1. User picks the source document and an apply mode (insert / merge / replace).
2. System enqueues an async WBS AI job and returns immediately with a job ID.
3. Worker runs map-reduce extraction on the document and reconstructs the hierarchy.
4. Extraction is cached against the document's content hash.
5. Result is presented as a draft WBS tree with a collision report.
6. User reviews and clicks **Apply**.
7. Deterministic safety nets validate codes, parent links and uniqueness.
8. On success, the WBS is committed to the project schedule.

**Postcondition:** The proposed WBS is applied (or rejected with a collision list).

### UC-AI-12: Generate Activities under a WBS Branch

| Attribute | Value |
|---|---|
| **ID** | UC-AI-12 |
| **Name** | Generate Activities under a WBS Branch |
| **Actor** | Planner / Scheduler |
| **Precondition** | WBS exists; relevant documents are uploaded |
| **Trigger** | User selects a WBS node and clicks **Generate Activities (AI)** |

**Main Flow:**
1. System builds a context bundle from the chosen WBS branch and the uploaded documents.
2. Async activity-generation job runs on a background worker.
3. Result lists proposed activities with durations, predecessors and resource hints.
4. Activity relationship validator checks for circular logic, invalid lags and end-node conflicts.
5. User reviews the draft and applies it.

**Postcondition:** New activities are inserted under the selected WBS branch with valid logic.

### UC-AI-13: Find an Available Resource

| Attribute | Value |
|---|---|
| **ID** | UC-AI-13 |
| **Name** | Find an Available Resource |
| **Actor** | Resource Manager |
| **Precondition** | Resource master, calendars and current deployments are loaded |
| **Trigger** | User asks the assistant *"who's free next week with role Foreman?"* |

**Main Flow:**
1. ReAct loop calls `find_resource_deployment` with the role filter and date window.
2. Tool reads the resource facade and returns matching profiles with utilisation percentages.
3. Assistant narrates the result and renders a utilisation bar chart inline.

**Postcondition:** A ranked list of available resources is shown.

### UC-AI-14: Read a Cached Insight Panel

| Attribute | Value |
|---|---|
| **ID** | UC-AI-14 |
| **Name** | Read a Cached Insight Panel |
| **Actor** | Project Manager / Executive |
| **Precondition** | The page hosts an `AiInsightsPanel` |
| **Trigger** | User opens the page |

**Main Flow:**
1. Frontend requests the insight by domain key and project scope.
2. Backend collector gathers a deterministic snapshot and computes its data hash.
3. If a cached insight matches the hash, it is returned immediately.
4. Otherwise the generator produces a new MDX document with embedded ECharts specs, stores it under the hash and returns it.
5. Frontend mounts the MDX and renders the narrated insight inline.

**Postcondition:** A consistent, narrated insight is displayed with no live LLM call when cached.

### UC-AI-15: Review Conversation History

| Attribute | Value |
|---|---|
| **ID** | UC-AI-15 |
| **Name** | Review Conversation History |
| **Actor** | Project Manager |
| **Precondition** | User has previously chatted with the assistant |
| **Trigger** | User clicks the **History** icon in the chat panel |

**Main Flow:**
1. System lists prior conversations ordered by recency, each with its smart title.
2. User selects a conversation to reopen.
3. Full message history, tool calls and inline charts are restored.
4. User can continue the conversation or export it as **CSV**, **XLSX** or **PDF**.

**Postcondition:** The conversation is reopened in the panel.

### UC-AI-16: Apply AI Output through Safety Nets

| Attribute | Value |
|---|---|
| **ID** | UC-AI-16 |
| **Name** | Apply AI Output through Safety Nets |
| **Actor** | Planner / Scheduler |
| **Precondition** | An AI WBS or activity draft exists |
| **Trigger** | User clicks **Apply** |

**Main Flow:**
1. Validator runs uniqueness, hierarchy and relationship checks.
2. Calendar and date-range sanity checks run against project settings.
3. Apply-mode rules (insert / merge / replace) detect collisions.
4. On any failure, apply is blocked and the offending items are highlighted.
5. On success, the schedule is updated atomically.

**Postcondition:** Either the schedule is updated or the user sees a precise collision report — never a partial write.

## Conversation Data Model

| Entity | Description |
|---|---|
| **AiConversation** | Top-level chat thread, with scope (project / activity / general), smart title and timestamps |
| **AiMessage** | A single user or assistant turn, including any image attachment URL |
| **AiToolCall** | A tool invocation made by the assistant during a turn, with arguments, result and latency |
| **AiInsightCache** | A cached MDX insight keyed by domain, scope and data hash |
| **WbsAiJob** / **WbsAiExtractionCache** | Async generation job state and reusable extraction results |

## Provider Configuration

LLM providers are configured by the System Administrator via the **LLM Provider Admin** screen. The system supports OpenAI-compatible providers, with separate models selectable for chat, generation and insight workloads. Provider keys are stored encrypted and are never exposed to the browser.

## Related Modules

- [AI & Predictions](../ai-predictions/) — schedule health, cost forecasting and trend analytics
- [Predictions](../../projects/predictions) — user-facing predictions tab
- [Activities & Scheduling](../activities-scheduling/)
- [Resource Management](../resource-management/)
- [Documents & Drawings](../documents-drawings/)
- [Security & Access Control](../security-access-control/)
