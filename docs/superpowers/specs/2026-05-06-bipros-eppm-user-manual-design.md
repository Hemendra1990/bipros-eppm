# Bipros EPPM Comprehensive User Manual — Design Spec

**Date:** 2026-05-06
**Status:** Approved
**Scope:** Expand the existing `user-guide/` Docusaurus documentation into a complete user manual covering all 20+ backend modules, every formula, every use case, all actors, and both end-user workflows and admin configuration.

---

## 1. Goals

1. Serve **both end users and system administrators** in a single manual.
2. Document **all 20+ backend modules** with equal depth.
3. Capture **every formula** (EVM, CPM, resource leveling, risk Monte Carlo, cost rollup, etc.) with MathJax rendering, variable tables, numeric examples, and interpretation rules.
4. Document **all actors** and their **use cases** using a standard template.
5. Preserve and expand existing `user-guide/` content — do not discard existing docs.
6. Enable cross-linking between Task Guides and Module Reference.
7. Be buildable and deployable as a Docusaurus static site.

---

## 2. Architecture

### 2.1 Top-Level Information Architecture

The Docusaurus sidebar uses a **hybrid structure** with three top-level buckets plus appendices:

```
📘 Bipros EPPM User Manual
├── 🚀 Getting Started
│   ├── System Overview
│   ├── Login & Navigation
│   ├── User Roles & Permissions
│   └── User Interface Guide
│
├── 📋 Task-Based Guides
│   ├── Creating Your First Project
│   ├── Setting Up the WBS
│   ├── Scheduling Activities with CPM
│   ├── Tracking Daily Progress (DPR)
│   ├── Managing Running Account Bills
│   ├── Resource Planning & Deployment
│   ├── Conducting Risk Analysis
│   ├── Running Schedule Compression
│   ├── Managing Permits
│   ├── Using GIS & Satellite Imagery
│   ├── Closing a Project
│   └── (additional task guides as needed)
│
├── 📚 Module Reference
│   ├── Enterprise Structure (EPS, OBS)
│   ├── Projects & WBS
│   ├── Activities & Scheduling
│   ├── Cost Management
│   ├── Earned Value Management (EVM)
│   ├── Resource Management
│   ├── Risk Management
│   ├── Baselines
│   ├── Contracts & RA Bills
│   ├── Documents & Drawings
│   ├── GIS & Satellite Imagery
│   ├── Permits
│   ├── Reports & Analytics
│   ├── Dashboards
│   ├── Integrations (PFMS, GeM, GSTN, CPPP, PARIVESH)
│   ├── AI & Predictions
│   ├── Security & Access Control
│   └── Administration
│
├── 🔤 Glossary & Abbreviations
└── 📎 Appendices
    ├── Formula Reference Sheet
    ├── Actor–Use Case Matrix
    └── Permission Matrix
```

### 2.2 Existing Content Preservation

Existing files in `user-guide/docs/` are preserved and expanded:

| Existing Path | Action |
|---|---|
| `getting-started/` | Expand with roles & permissions |
| `projects/` | Expand with deep-dive formulas, use cases, configuration |
| `dashboards/` | Expand with KPI definitions, dashboard configuration |
| `admin/` | Expand with full RBAC, integration setup |
| `resources/` | Expand with resource curves, leveling, productivity norms |
| `risk/` | Expand with Monte Carlo detail, scoring matrices |
| `enterprise-structure/` | Expand with EPS/OBS hierarchy rules |
| `portfolios/` | Expand with portfolio analytics |
| `reports-analytics/` | Expand with report builder, KPI engine |
| `glossary.md` | Expand with module-specific terms |

---

## 3. Content Standards

### 3.1 Module Reference Template

Every module in **Module Reference** must contain:

1. **Overview** — What the module does, its purpose, and who uses it.
2. **Actors & Roles** — A table of actors who interact with this module.
3. **Use Cases** — Numbered use cases in the standard format (see §3.3).
4. **Screens & UI Walkthrough** — Screenshots with callouts; reuse existing screenshots where available.
5. **Data Model / Key Entities** — Core entities, their fields, and relationships.
6. **Formulas & Calculations** — All formulas in MathJax with variable tables, numeric examples, and interpretation rules.
7. **Business Rules & Validations** — Constraints, auto-calculations, edge cases.
8. **Configuration (Admin)** — Settings, templates, master data setup.
9. **Permissions** — Which roles can read / write / admin.
10. **Integration Points** — APIs, webhooks, external systems.
11. **Related Modules** — Cross-links to other module docs.

### 3.2 Formula Documentation Standard

All formulas use **MathJax** (Docusaurus supports `$$...$$` blocks). Each formula entry must contain:

1. **Display formula** in LaTeX.
2. **Variable definitions table**.
3. **Concrete numeric example**.
4. **Interpretation rules** (what does >1 mean, what does negative mean, thresholds).

Example:

```markdown
### Cost Performance Index (CPI)

$$CPI = \frac{EV}{AC}$$

| Variable | Definition |
|---|---|
| $EV$ | Earned Value = Budget × Actual % Complete |
| $AC$ | Actual Cost = Sum of all recorded expenditures |

**Example:** If $EV = ₹50,00,000$ and $AC = ₹55,00,000$:
$$CPI = \frac{50,00,000}{55,00,000} = 0.91$$

**Interpretation:** $CPI < 1.0$ indicates the project is over budget.
```

### 3.3 Use Case Standard Format

```markdown
### UC-{MODULE}-{NNN}: {Use Case Name}

| Attribute | Value |
|---|---|
| **ID** | UC-{MODULE}-{NNN} |
| **Name** | {Use Case Name} |
| **Actor** | {Actor Name} |
| **Precondition** | {What must be true before} |
| **Trigger** | {What starts the use case} |

**Main Flow:**
1. {Step 1}
2. {Step 2}
3. ...

**Alternative Flows:**
- {StepRef}a. {Condition} → {System/User response}

**Postcondition** | {What is true after} |
```

### 3.4 Cross-Linking Standard

- Task guides link to relevant Module Reference pages with `See [EVM Formulas](/module-reference/evm#formulas)`.
- Module Reference pages link to related modules and relevant Task Guides.
- Use Docusaurus `sidebar_position` to control ordering.
- Every module doc must have `title`, `description`, and `sidebar_position` front matter.

---

## 4. Actors

| Actor | Description | Typical Permissions |
|---|---|---|
| **System Administrator** | Manages users, roles, orgs, integrations, templates | Full system access |
| **Programme Manager (PMO)** | Oversees multiple projects and portfolios | Portfolio read/write, cross-project reports |
| **Project Manager** | Owns project planning, execution, and closure | Project CRUD, WBS, schedule, cost, EVM |
| **Planning Engineer** | Handles CPM scheduling, baselines, compression | Schedule write, baseline create |
| **Cost Engineer** | Manages budgets, BOQ, RA bills, forecasts | Cost write, budget change, RA bill process |
| **Site / Field Engineer** | Daily site operations and data entry | DPR create, labour returns, weather, equipment logs |
| **Resource Manager** | Allocates labour, equipment, materials | Resource deployment, curves, leveling |
| **Contract Manager** | Handles contracts and payment processing | Contract CRUD, RA bill approval |
| **Risk Manager** | Identifies, analyses, and mitigates risks | Risk register, Monte Carlo run, template manage |
| **Document Controller** | Manages drawings, RFIs, documents | Upload, version control, distribution |
| **Permit Officer** | Handles statutory and regulatory permits | Permit application, tracking, approval |
| **GIS Analyst** | Spatial data, polygons, satellite imagery | GIS layer edit, satellite ingestion |
| **Executive / Stakeholder** | Read-only dashboards, KPIs, predictions | Dashboard read, report read |
| **External System** | PFMS, GeM, GSTN, CPPP, PARIVESH | API-based data exchange |

---

## 5. Formula Coverage Checklist

The following formulas must be documented with MathJax, examples, and interpretations:

### 5.1 Earned Value Management (EVM)
- [ ] Planned Value (PV)
- [ ] Earned Value (EV)
- [ ] Actual Cost (AC)
- [ ] Cost Variance (CV)
- [ ] Schedule Variance (SV)
- [ ] Cost Performance Index (CPI)
- [ ] Schedule Performance Index (SPI)
- [ ] Budget at Completion (BAC)
- [ ] Estimate at Completion (EAC) — multiple methods
- [ ] Estimate to Complete (ETC)
- [ ] Variance at Completion (VAC)
- [ ] To-Complete Performance Index (TCPI)
- [ ] EVM Techniques: 0/100, 50/50, Percent Complete, Level of Effort (LOE)
- [ ] Rollup logic (WBS → Activity → Project)

### 5.2 CPM Scheduling
- [ ] Early Start (ES) — Forward Pass
- [ ] Early Finish (EF) — Forward Pass
- [ ] Late Start (LS) — Backward Pass
- [ ] Late Finish (LF) — Backward Pass
- [ ] Total Float (TF)
- [ ] Free Float (FF)
- [ ] Critical Path identification
- [ ] Dependency types: FS, SS, FF, SF with lags/leads
- [ ] PERT Estimation (Optimistic, Most Likely, Pessimistic)

### 5.3 Schedule Compression
- [ ] Crash Cost Slope
- [ ] Cost per day saved
- [ ] Compression recommendation algorithm

### 5.4 Resource Management
- [ ] Resource Curves (front-loaded, back-loaded, uniform)
- [ ] Resource Leveling algorithm
- [ ] Resource Smoothing algorithm
- [ ] Capacity Utilization Rate
- [ ] Productivity Norm calculations

### 5.5 Risk Analysis
- [ ] Risk Score = Probability × Impact
- [ ] Expected Monetary Value (EMV)
- [ ] PERT / Triangular Distribution for Monte Carlo
- [ ] Beta Distribution (PERT)
- [ ] Iman-Conover correlation
- [ ] P50, P80, P90 percentile interpretation

### 5.6 Cost Management
- [ ] BOQ Item Cost Rollup
- [ ] RA Bill Amount Calculation
- [ ] Budget Change Log
- [ ] Cash Flow Forecast
- [ ] Period Cost Aggregation
- [ ] Cost Account Hierarchy Rollup

### 5.7 Custom Formulas (UDF)
- [ ] Formula Engine syntax
- [ ] Built-in functions
- [ ] Override rules

---

## 6. Identified Gaps (New Content Needed)

| Module / Area | Gap | Priority |
|---|---|---|
| Activities & Scheduling | CPM algorithm, float, dependency deep-dive, scenario comparison | High |
| EVM | TCPI, technique strategies (0/100, 50/50, LOE), rollup logic | High |
| Resource Management | Resource curves, leveling/smoothing algorithms, productivity norms | High |
| Risk / Monte Carlo | Simulation engine, distributions, correlation, percentile interpretation | High |
| Cost / Budget / RA Bills | Cost accounts, funding sources, cash flow, period performance | High |
| Calendar / Baselines | Working calendars, holidays, baseline creation and comparison | Medium |
| Permits | Full permit lifecycle, templates, approval workflow | Medium |
| GIS | Polygon mapping, satellite ingestion, construction progress overlay | Medium |
| AI / Predictions / Analytics | ML model inputs, schedule health scoring, capacity insights | Medium |
| Security & RBAC | Profiles, roles, project-level access control matrix | High |
| Integrations | PFMS, GeM, GSTN, CPPP, PARIVESH — setup, data flow, error handling | Medium |
| UDF / Custom Formulas | Formula engine syntax, built-ins, overrides | Medium |
| Document Management | Full lifecycle, version control, MinIO storage config | Low |
| Equipment Logs / Material Reconciliation | Partial coverage, needs completion | Low |
| Import / Export | Data migration, Excel/CSV templates | Low |
| Analytics Dashboards | Programme, Executive, Field, Operational deep-dive | Medium |

---

## 7. Docusaurus Configuration

- **MathJax support:** Enable `@docusaurus/plugin-content-docs` with `remarkMath` and `rehypeKatex` for LaTeX rendering.
- **Sidebar:** Use `sidebars.ts` with autogenerated categories for each module bucket.
- **Search:** Ensure local search plugin is configured (e.g., `@cmfcmf/docusaurus-search-local`).
- **Static assets:** Store screenshots in `user-guide/static/img/screenshots/` with naming convention `{module}-{feature}.png`.

---

## 8. Acceptance Criteria

1. Every backend module has at least one documented page in Module Reference.
2. Every formula in §5 is documented with MathJax, variable table, numeric example, and interpretation.
3. Every actor in §4 has documented use cases in at least one module.
4. All existing `user-guide/docs/` content is preserved and linked.
5. The Docusaurus site builds without errors (`yarn build` succeeds).
6. Cross-links between Task Guides and Module Reference exist for all major workflows.

---

## 9. Out of Scope

- API developer documentation (OpenAPI/Swagger already exists at `/swagger-ui.html`).
- Deployment / DevOps documentation (covered in `AGENTS.md`).
- Frontend component library docs.
- Third-party integration API specs (document only Bipros-side setup and data flow).

---

## 10. Files

- **Design spec:** `docs/superpowers/specs/2026-05-06-bipros-eppm-user-manual-design.md` (this file)
- **Implementation plan:** To be created by `writing-plans` skill after spec approval
- **Output directory:** `user-guide/docs/` (existing Docusaurus site)
