---
sidebar_position: 6
title: Field Dashboard
description: Site-level activities, labour deployment, and real-time work progress
---

# Field Dashboard

The **Field Dashboard** is designed for **Site Engineers and Supervisors** who need immediate visibility into what is happening on the ground today. It focuses on daily operational data rather than long-term trends and renders the **compact-density** Manpower and Equipment KPI sections — headline cards only, without the per-activity supporting tables shown on the Operational dashboard.

![Field Dashboard](/img/screenshots/14-dashboard-field.png)

## Key Sections

### Site-Level Activity Tracking

A list of activities currently in progress at the site level:

| Column | Description |
|---|---|
| **Activity Name** | The activity being executed today |
| **WBS Element** | Which part of the WBS this activity belongs to |
| **Planned Duration** | Original planned duration in days |
| **Remaining Duration** | Days of work remaining |
| **% Complete** | Current physical completion percentage |
| **Status** | In Progress, Not Started, or Completed |

### Real-Time Work Progress

Visual indicators showing:

- **Today's planned work** vs. **work actually completed**
- **Cumulative progress** for the current reporting period
- **Deviation alerts** when actual progress falls behind planned targets

### Labour Deployment Status

| Metric | Description |
|---|---|
| **Total Headcount** | Number of workers present on site today |
| **By Skill Category** | Breakdown into Skilled, Semi-Skilled, Unskilled, Supervisor, and Engineer categories |
| **Man-Days** | Total man-days logged for the current period |
| **Attendance Rate** | Percentage of expected labour force actually present |

### Manpower & Equipment KPIs (compact)

The Field tier shows **headline KPI cards only** — no per-activity tables. From the Manpower section: **Workforce Utilisation** and **Avg Productivity Factor**. From the Equipment section: **Avg Utilisation %** and **Service Due (next 7 days)**. Each card has a formula tooltip; for the full breakdown with bottom-5 activity tables and Labour Cost / Unit, switch to the Operational dashboard.

### Equipment Utilization

| Metric | Description |
|---|---|
| **Equipment Deployed** | Number of machines and vehicles currently at the site |
| **Operating Hours** | Total productive hours logged today |
| **Idle Hours** | Hours equipment was available but not in use |
| **Breakdown Hours** | Hours lost due to equipment failure |
| **Fuel Consumption** | Litres of fuel consumed by each piece of equipment |

### Material Consumption

| Metric | Description |
|---|---|
| **Materials Received** | Quantities delivered to site in the current period |
| **Materials Consumed** | Quantities used in construction activities |
| **Wastage** | Quantities lost or wasted during construction |
| **Closing Stock** | Remaining inventory at the site |

---

## Abbreviations Used

| Abbreviation | Full Form |
|---|---|
| WBS | Work Breakdown Structure |
| MT | Metric Tonnes |
| cum | Cubic Metres |
| RMT | Running Metres |
