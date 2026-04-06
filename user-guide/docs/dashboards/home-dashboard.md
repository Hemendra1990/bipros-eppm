---
sidebar_position: 2
title: Home Dashboard
description: The landing page after login — project status summary and quick actions
---

# Home Dashboard

The **Home Dashboard** is the first screen you see after signing in. It provides an at-a-glance summary of your entire project portfolio with quick-access links to common actions.

![Home Dashboard](/img/screenshots/01-dashboard.png)

## Sections

### Project Status Cards

Four cards across the top summarize your project portfolio:

| Card | Description |
|---|---|
| **PLANNED** | Number of projects in the `PLANNED` status — not yet started |
| **ACTIVE** | Number of projects currently in progress (`ACTIVE` status) |
| **COMPLETED** | Number of projects that have reached completion |
| **RESOURCES** | Total number of resources (labour, equipment, materials) defined in the system |

Each card displays a large count value with an icon. Use these to quickly gauge the health of your portfolio.

### Activity Summary

Three cards summarizing activity-level metrics across all projects:

| Card | Description |
|---|---|
| **TOTAL ACTIVITIES** | The total count of all activities across all projects |
| **CRITICAL ACTIVITIES** | Activities on the critical path (zero total float) — delays to these directly delay the project |
| **OVERDUE** | Activities that have passed their planned finish date without being marked complete |

### Recent Projects Table

A table listing the five most recently created projects. Each row displays:

| Column | Description |
|---|---|
| **Project Name** | Clickable link — takes you directly to that project's detail view |
| **Status** | Current project status badge (`PLANNED`, `ACTIVE`, or `COMPLETED`) |
| **Created** | Date the project was created, formatted as `DD/MM/YYYY` |

### Quick Actions

Below the table, **Quick Action** buttons provide shortcuts to common tasks:

- **New Project** — Jump directly to the project creation form
- **View Reports** — Navigate to the Reports module
- **Manage Resources** — Navigate to the Resources page

---

## What to Do From Here

- **Click a project name** in the Recent Projects table to open its detail view
- **Check the Overdue count** — if non-zero, investigate which activities need attention
- **Review Critical Activities** — a high count means many tasks are on the critical path with no schedule buffer
