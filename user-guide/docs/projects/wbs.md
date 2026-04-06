---
sidebar_position: 6
title: Work Breakdown Structure (WBS)
description: Organizing project scope into a hierarchical decomposition of deliverables
---

# Work Breakdown Structure (WBS)

The **Work Breakdown Structure (WBS)** is a hierarchical decomposition of the total scope of a project into manageable work packages. It is the foundation for all scheduling, cost tracking, and progress measurement in Bipros EPPM.

## Accessing the WBS

Navigate to a project and click the **WBS** tab.

![WBS Tab](/img/screenshots/37-project-wbs.png)

## Understanding the WBS Hierarchy

The WBS is structured as a tree:

```
Project
├── 1.0 Earthworks
│   ├── 1.1 Site Clearing
│   ├── 1.2 Excavation
│   └── 1.3 Embankment
├── 2.0 Structural Works
│   ├── 2.1 Foundations
│   ├── 2.2 Substructure
│   └── 2.3 Superstructure
└── 3.0 Road Works
    ├── 3.1 Sub-base
    ├── 3.2 Base Course
    └── 3.3 Wearing Course
```

### WBS Node Properties

Each WBS node has the following attributes:

| Field | Description |
|---|---|
| **WBS Code** | Hierarchical code (e.g., `1.0`, `1.2`, `2.3.1`). Follows a dotted numbering system. |
| **WBS Name** | Descriptive name of the work package or deliverable |
| **Parent** | The parent WBS node (except for the root level) |
| **Description** | Optional detailed description of the scope of this element |

## Creating WBS Nodes

1. Click **Add WBS Node** (or **New Node**)
2. Fill in:
   - **WBS Code** — Must be unique within the project
   - **WBS Name** — Descriptive name
   - **Parent** — Select the parent node to place it in the hierarchy
3. Click **Save**

## WBS and Activities

Activities are assigned to WBS nodes at the lowest level (leaf nodes). This enables:

- **Bottom-up cost aggregation** — Costs roll up from activities to parent WBS nodes
- **Progress tracking** — Physical completion percentages roll up through the hierarchy
- **Reporting** — Generate reports at any level of the WBS

## WBS Templates

Administrators can create reusable WBS templates for common project types. See [WBS Templates (Admin)](../admin/wbs-templates) for details.

Available built-in templates:

| Template | Asset Class |
|---|---|
| **ROAD** | Road and highway projects |
| **RAIL** | Railway and metro projects |
| **POWER** | Power generation and transmission projects |
| **WATER** | Water supply and treatment projects |
| **ICT** | Information and communication technology projects |
| **BUILDING** | Building and facility construction projects |
| **GREEN_INFRASTRUCTURE** | Green infrastructure and environmental projects |

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| WBS | Work Breakdown Structure |
