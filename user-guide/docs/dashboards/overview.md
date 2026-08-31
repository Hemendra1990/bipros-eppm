---
sidebar_position: 1
title: Dashboards Overview
description: Four-tier dashboard system for every stakeholder level
---

# Dashboards Overview

Bipros EPPM provides a **four-tier dashboard system** designed to give each stakeholder the right level of detail. From high-level strategic views for executives to granular site-level data for field engineers, each dashboard tier presents relevant KPIs, charts, and tables.

## Dashboard Hub

Access the dashboard hub by clicking **Dashboards** in the sidebar. The hub presents four cards, each linking to a specific dashboard tier.

![Dashboard Hub](/img/screenshots/10-dashboards-hub.png)

| Dashboard Tier | Colour | Target Audience | Primary Focus |
|---|---|---|---|
| **Executive** | Purple | CXOs, Directors, Secretary-level officials | Corridor-level overview, strategic KPIs, top risks |
| **Programme** | Blue | Programme Directors, PMO heads | EVM metrics, milestones, contractor performance |
| **Operational** | Green | Project Managers, Engineers | RA bills, resource utilization, WBS-level progress |
| **Field** | Orange | Site Engineers, Supervisors | Daily activities, labour deployment, equipment status |

Click any tier card to navigate to that dashboard.

## Choosing the Right Dashboard

- **Need a bird's-eye view across all corridors?** Use the **Executive** dashboard
- **Want to see EVM trends and contractor KPIs?** Use the **Programme** dashboard
- **Tracking billing and resource allocation?** Use the **Operational** dashboard
- **Monitoring today's site activity?** Use the **Field** dashboard

For detailed documentation on each tier, see the pages that follow.

## Role-based KPIs

Each dashboard tier surfaces a **different set of KPI cards** matched to the role that uses it. The same Manpower and Equipment KPI services power the lower tiers, but the cards rendered, the level of detail, and the supporting tables vary by role:

| Tier | Role Focus | Manpower KPIs Shown | Equipment KPIs Shown | Detail Level |
|---|---|---|---|---|
| **Executive** | CXO / corridor view | Aggregate workforce utilisation across portfolio | Aggregate equipment utilisation | Headline numbers only |
| **Programme** | PMO / programme office | EVM-style labour metrics, contractor scorecards | Equipment availability vs performance | Trend charts |
| **Operational** | Project manager / cost engineer | Workforce Utilisation, Total Labour Cost, Avg Productivity Factor, Under-Performing Activities; bottom-5 productivity, top-5 Labour Cost / Unit, bottom-5 Crew Output vs Norm | Avg Utilisation %, Idle Alerts, Fuel / Output, Availability vs Performance, Owned vs Rented, Service Due (next 7 days) | **Full** — `density="full"` (cards + per-activity tables) |
| **Field** | Site engineer / supervisor | Workforce Utilisation, Avg Productivity Factor (compact) | Avg Utilisation %, Service Due (compact) | **Compact** — `density="compact"` (headline cards only, no per-activity tables) |

Both Manpower and Equipment KPI sections expose **per-card formula tooltips** so users can see exactly how a number is computed. AI Insights are rendered below the KPIs and default to collapsed; expand them only when you want narrative context for the numbers.

The KPI services themselves are role-agnostic — the **density** prop on the KPI sections, plus the choice of which sections each dashboard renders, is what makes the experience role-specific.
