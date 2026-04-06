---
sidebar_position: 2
title: WBS Templates
description: Create and manage reusable Work Breakdown Structure templates
---

# WBS Templates

**WBS Templates** are pre-configured Work Breakdown Structure hierarchies that can be applied to new projects. They provide a starting point so project teams do not need to build the WBS from scratch for common project types.

:::info
This page is only accessible to users with the **Admin** role.
:::

## Accessing WBS Templates

Click **WBS Templates** in the sidebar.

![WBS Templates](/img/screenshots/16-admin-wbs-templates.png)

## Built-In Templates

Bipros EPPM includes templates for common infrastructure asset classes:

| Template Code | Asset Class | Description |
|---|---|---|
| **ROAD** | Road & Highway | Standard WBS for road construction projects (earthworks, pavement, drainage, structures) |
| **RAIL** | Railway & Metro | WBS for railway projects (track, signalling, stations, electrification) |
| **POWER** | Power Generation & Transmission | WBS for power projects (generation, transmission lines, substations) |
| **WATER** | Water Supply & Treatment | WBS for water projects (intake, treatment, distribution, storage) |
| **ICT** | Information & Communication Technology | WBS for ICT infrastructure projects (fibre, data centres, networking) |
| **BUILDING** | Building & Facility | WBS for building construction (foundations, structure, MEP, finishes) |
| **GREEN_INFRASTRUCTURE** | Green Infrastructure | WBS for environmental projects (parks, green corridors, stormwater) |

## Template Structure

Each template defines a multi-level WBS hierarchy. For example, the **ROAD** template includes:

```
ROAD Project
├── 1.0 Preliminaries
│   ├── 1.1 Mobilization
│   ├── 1.2 Site Setup
│   └── 1.3 Survey & Investigation
├── 2.0 Earthworks
│   ├── 2.1 Clearing & Grubbing
│   ├── 2.2 Excavation
│   └── 2.3 Embankment
├── 3.0 Pavement
│   ├── 3.1 Sub-base
│   ├── 3.2 Base Course
│   └── 3.3 Wearing Course
├── 4.0 Drainage
├── 5.0 Structures (Bridges, Culverts)
└── 6.0 Finishing & Handover
```

## Applying a Template to a Project

1. Create a new project (or open an existing project with no WBS)
2. Navigate to the **WBS** tab
3. Click **Apply Template** (or **Import from Template**)
4. Select the appropriate template
5. The WBS hierarchy is automatically created

:::tip
Templates provide a starting point. After applying a template, you can modify the WBS by adding, removing, or renaming nodes to suit your specific project.
:::

## Creating a Custom Template

1. Navigate to **Admin > WBS Templates**
2. Click **New Template**
3. Define the template code, name, and asset class
4. Build the WBS hierarchy with codes, names, and parent-child relationships
5. Click **Save**

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| WBS | Work Breakdown Structure |
| MEP | Mechanical, Electrical, and Plumbing |
| ICT | Information and Communication Technology |
