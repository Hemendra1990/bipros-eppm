---
sidebar_position: 1
title: Enterprise Project Structure (EPS)
description: Top-level organizational hierarchy for grouping projects
---

# Enterprise Project Structure (EPS)

The **Enterprise Project Structure (EPS)** defines the top-level organizational hierarchy of your entire project portfolio. It is the highest level of project grouping in Bipros EPPM, providing a tree structure under which all projects are organized.

## Accessing the EPS

Click **EPS** in the sidebar.

![EPS](/img/screenshots/04-eps.png)

## What Is the EPS?

The EPS is a hierarchical structure that organizes projects by organizational division, programme, region, or any other grouping meaningful to your enterprise. Think of it as a "folder structure" for projects.

### Example EPS Hierarchy

```
Enterprise
├── Infrastructure Division
│   ├── National Highways Programme
│   │   ├── NH-48 Extension
│   │   └── NH-66 Widening
│   └── State Roads Programme
│       ├── SR-12 Upgrade
│       └── SR-45 New Construction
├── Power Division
│   ├── Solar Programme
│   └── Transmission Programme
└── Water Division
    ├── Supply Programme
    └── Treatment Programme
```

## EPS Node Properties

| Field | Description |
|---|---|
| **EPS Code** | Unique identifier for this node in the hierarchy |
| **EPS Name** | Descriptive name of the organizational unit or programme |
| **Parent** | The parent EPS node (except for the root) |
| **Description** | Optional description of scope |

## Creating EPS Nodes

1. Click **Add EPS Node** (or **New Node**)
2. Fill in:
   - **EPS Code** — Unique identifier
   - **EPS Name** — Descriptive name
   - **Parent** — Select the parent node
3. Click **Save**

## EPS vs. WBS

These two structures serve different purposes:

| Structure | Scope | Purpose |
|---|---|---|
| **EPS** | Enterprise-wide | Organizes *projects* within the enterprise hierarchy |
| **WBS** | Single project | Organizes *work packages* within a single project |

The EPS sits above the WBS — projects are placed under EPS nodes, and each project has its own WBS.

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| EPS | Enterprise Project Structure |
| WBS | Work Breakdown Structure |
