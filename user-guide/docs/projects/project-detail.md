---
sidebar_position: 3
title: Project Detail View
description: Understanding the project overview page and navigating between project tabs
---

# Project Detail View

When you click a project name from the Projects list, you enter the **Project Detail View**. This is the central workspace for managing everything within a single project.

![Project Overview](/img/screenshots/20-project-overview.png)

## Overview Tab

The **Overview** tab is the default view when you open a project. It displays:

### Project Information

| Field | Description |
|---|---|
| **Project Code** | The unique identifier for this project |
| **Project Name** | Full project name |
| **Status** | Current status: `PLANNED`, `ACTIVE`, or `COMPLETED` |
| **Start Date** | Planned or actual start date |
| **Finish Date** | Planned or actual finish date |
| **Priority** | Priority level (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) |
| **Description** | Detailed description of the project scope |
| **Created Date** | When the project was created in the system |
| **Last Updated** | Most recent modification timestamp |

### Key Metrics

Summary cards showing important project metrics at a glance:

| Metric | Description |
|---|---|
| **Total Activities** | Number of activities defined in this project |
| **Critical Activities** | Number of activities on the critical path |
| **Progress** | Overall physical completion percentage |
| **Budget** | Total budgeted cost of the project |

## Tab Navigation

The project detail view includes a horizontal tab bar with the following tabs:

### Primary Tabs (Always Visible)

| Tab | Purpose | Key Data |
|---|---|---|
| **Overview** | Project summary and key metrics | Status, dates, description |
| **WBS** | Work Breakdown Structure hierarchy | WBS codes, names, parent-child structure |
| **Activities** | Activity list and scheduling data | Activity codes, durations, dates, dependencies |
| **Gantt** | Visual Gantt chart of the schedule | Timeline bars, milestones, dependencies |
| **Resources** | Resources assigned to this project | Resource names, types, allocations |
| **Costs** | Cost tracking and budget data | Budget, actual, variance |
| **EVM** | Earned Value Management dashboard | PV, EV, AC, CPI, SPI charts |

### Secondary Tabs

| Tab | Purpose |
|---|---|
| **Contracts** | Contract management for this project |
| **Documents** | Document library with folder hierarchy |
| **GIS** | Geographic map viewer |

### More Menu

Additional modules accessed via the **More (...)** dropdown:

| Item | Purpose |
|---|---|
| **Schedule Health** | Schedule quality scoring and float analysis |
| **Schedule Compression** | Fast-tracking and crashing analysis |
| **Risk Analysis** | Risk register and Monte Carlo simulation |
| **Predictions** | AI-powered project forecasting |
| **RA Bills** | Running Account billing management |
| **Drawings** | Engineering drawing management |
| **RFIs** | Requests for Information |
| **Equipment Logs** | Equipment deployment tracking |
| **Labour Returns** | Daily labour records |
| **Materials** | Material reconciliation |

## Editing a Project

To edit project details:

1. Navigate to the **Overview** tab
2. Click the **Edit** button
3. Modify the fields you need to change
4. Click **Save** to persist your changes

## Deleting a Project

To delete a project:

1. Navigate to the **Overview** tab
2. Click the **Delete** button
3. Confirm the deletion in the confirmation dialog

:::caution
Deleting a project permanently removes all associated data, including activities, resources, contracts, documents, and baselines. This action cannot be undone.
:::
