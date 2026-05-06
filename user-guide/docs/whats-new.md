---
sidebar_position: 0
title: What's New
description: Recent enhancements and changes in Bipros EPPM
slug: /whats-new
---

# What's New

This page lists recent user-visible enhancements to Bipros EPPM. Newest changes are at the top.

## May 2026

### Faster, more powerful tables

Lists across the application now use a new high-performance data table. You will notice:

- **Sticky headers** — column headers stay pinned at the top while you scroll long lists.
- **Drag-to-resize columns** — grab the right edge of any column header and drag to widen or narrow.
- **Per-column filter bar** — filter on individual columns in addition to the existing global search.
- **Virtual scrolling** — long lists (thousands of rows) scroll smoothly without manual pagination.

Smaller lists (under 50 rows) use a lighter variant with the same look and feel — no virtualization overhead, but the same sticky headers and filter bar.

![Filter bar above the Daily Outputs table](/img/release-notes/daily-outputs-filters.png)

### Daily Progress Report (DPR) & Daily Outputs improvements

The DPR workflow has been redesigned for faster daily entry on site:

- **DPR drawer** — creating or editing a DPR now opens a right-side drawer with a header summary and clearly sectioned form (Weather, Resources, Activity Outputs, Next-Day Plan). You can keep the Daily Outputs list visible underneath while you work.
- **Daily Outputs filter bar** — filter outputs by activity, date, or status directly from the column headers.

![Redesigned DPR drawer](/img/release-notes/dpr-drawer.png)

For the full step-by-step DPR walkthrough, see [Tracking Daily Progress](./task-guides/tracking-daily-progress).

### Resource management — 3-tier model is now canonical

The legacy "Crews" and "Resource Hierarchy" pages have been retired. All resource configuration now flows through the unified three-tier model: **Project → Project Resource → Resource**.

If you previously bookmarked the older crews or resource-hierarchy pages, use the [Resource Management module reference](./module-reference/resource-management/) instead.

### WBS Tree → Activity navigation fixed

Clicking **View** on an activity row inside the WBS Tree no longer briefly freezes the UI before the activity detail page opens.

## Earlier in 2026

A consolidated round-up of features that landed before the May 2026 release and are now fully documented in the user guide.

### Permission profiles & per-user assignment

Administrators can now create reusable **Permission Profiles** — named bundles of fine-grained permissions — and assign one to each user. Profiles are configured under **Admin → Permission Profiles** and assigned in **Admin → Users**. See [Security & Access Control — Permission Profiles](./module-reference/security-access-control/#permission-profiles-admin-ui).

### Theme system, palette builder, and branding

A full theming system is now available under **Admin → Settings**:

- Pick from a gallery of **predefined palettes** or build a **custom theme** with light/dark colours, border radius, and font family.
- Customise the **app name** (primary/secondary) and **light/dark logos** as part of the theme record — branding now travels with the theme.
- Active theme is persisted **per user** on the backend; the custom theme catalogue is **org-wide**; CSS is cached **per device** for fast load.

See [Theme & Palette](./admin/settings#theme--palette).

### Editorial split-screen sign-in

The login page has been redesigned with an editorial layout: the sign-in card is paired with a live programme telemetry strip (mini-Gantt + KPI tiles) and a feature highlight grid. A `Remember me` checkbox keeps you signed in for longer, and a `next=` query parameter on the URL controls where you land after login. See [Login Screen](./getting-started/navigation#login-screen).

### AI Assistant — conversational, agentic, document-aware

The AI panel has grown from a predictions surface into a full assistant:

- **Conversational chat** with per-conversation memory and auto-generated **smart titles**.
- **Agentic ReAct loop** — the assistant can call project, activity, resource, baseline, and forecasting tools and explains its reasoning in plain language.
- **Inline ECharts** rendered server-side with **MDX-narrated insights**, plus per-message **copy** and **maximize** actions.
- **Cached AI insights** with per-domain collectors so panels render instantly.
- **Document-aware WBS & activity generation** — upload project documents and the assistant generates schedule scaffolding grounded in those documents, via async jobs with caching and **deterministic safety nets** that validate every output before it hits the schedule.
- **Role-aware deployment search** — find resources, summarise a project's manpower, or forecast completion through natural language.

See the new [AI Assistant — Deep Dive](./module-reference/ai-assistant/) page.

### BOQ commercial chain

Bill of Quantities, contractor pricing, variation orders, and RA bills are now linked end-to-end. BOQ items roll up into project budget, feed into earned-value calculations, and seed RA bill drafts automatically. See [Cost Management — BOQ Commercial Chain](./module-reference/cost-management/#boq-commercial-chain).

### Role-based KPI dashboards

The dashboard hub now surfaces different KPI cards based on the dashboard tier you open:

- **Field** dashboards render compact-density manpower & equipment KPIs for site teams.
- **Operational** dashboards render full-density KPIs (Workforce Utilisation, Avg Productivity Factor, Idle Alerts, Service Due, etc.) for resource managers.
- **Programme** and **Executive** dashboards focus on portfolio-level signals.

See [Dashboards — Role-based KPIs](./dashboards/overview#role-based-kpis).

### Baseline maintenance — slots, comparison, restore

Baselines are now first-class artefacts with three independent slots (`PRIMARY`, `SECONDARY`, `TERTIARY`) and a full maintenance lifecycle: create, assign to slot, compare, selectively update, restore (preserves actuals), and clear. A **re-baseline banner** highlights when a project's baseline has drifted from the current schedule. See [Baselines — Deep Dive](./module-reference/baselines/).

### Resource management — 3-tier model, time-phased view, manpower master

In addition to the legacy crews / hierarchy retirement called out at the top of this page:

- The **3-tier model** (Project → ProjectResource → Resource) is now the canonical hierarchy and is fully described in the [Resource Management deep-dive](./module-reference/resource-management/#three-tier-resource-model).
- A **time-phased Resource Usage view** rolls up planned and actual quantities by period.
- The **Manpower Master** (categories, skills, employment types, levels, nationalities) lives under **Admin** and feeds resource and DPR forms.
- **Cost rate is unified on `Resource.costPerUnit`** — the legacy `ResourceRole.defaultRate` field has been retired.
- The assignment view is **role-grouped** with a "remaining backfill" indicator per role.
- **Capacity-utilisation Excel export** is available from the Capacity Utilisation report.
