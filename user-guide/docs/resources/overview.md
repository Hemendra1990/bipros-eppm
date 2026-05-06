---
sidebar_position: 1
title: Resources
description: Manage labour, equipment, and material resources
---

# Resources

The **Resources** module is the central repository for all labour, equipment, and material resources available to your projects. Resources defined here can be admitted into a project's pool and then assigned to project activities for scheduling, cost tracking, and utilisation analysis.

## Three-Tier Resource Model

Bipros EPPM separates resource definition, project reservation, and activity assignment into three explicit tiers:

| Tier | Where it lives | Purpose |
|---|---|---|
| **1. Master Resource** | This screen (organisation-wide) | Code, name, type, role, calendar, default rate (`costPerUnit`), max units/day |
| **2. Project Pool** (`ProjectResource`) | Inside a project's **Resources → Pool** tab | Admits a master resource into a specific project; carries optional rate / availability overrides for that project |
| **3. Assignment** | On an activity | Planned units, dates, curve, planned/actual/remaining cost |

A resource must be in the project pool before it can be assigned to an activity. The cost rate used on assignments resolves as `ProjectResource.rateOverride` → `Resource.costPerUnit`. Role-level rates (the legacy `ResourceRole.defaultRate`) are no longer used for cost rollups.

For the full model, formulas, and use cases see the [Resource Management deep dive](../module-reference/resource-management/).

## Accessing Resources

Click **Resources** in the sidebar.

![Resources](/img/screenshots/06-resources.png)

## Resources Table

| Column | Description |
|---|---|
| **Resource Code** | Unique identifier for the resource (e.g., `LAB-001`, `EQP-CRANE-01`) |
| **Resource Name** | Descriptive name (e.g., "Senior Structural Engineer", "50T Mobile Crane") |
| **Resource Type** | Classification: LABOR, NON_LABOR (equipment), or MATERIAL |
| **Status** | Current status: `ACTIVE` or `INACTIVE` |
| **Max Units/Day** | Maximum availability per day (e.g., 1.0 = one full unit per day, 0.5 = half-time) |

## Resource Types

| Type | Description | Examples |
|---|---|---|
| **LABOR** | Human resources — workers, engineers, supervisors | Skilled Mason, Site Engineer, Project Manager |
| **NON_LABOR** | Equipment and machinery | Excavator, Tower Crane, Concrete Mixer |
| **MATERIAL** | Consumable materials | Cement, Steel Rebar, Aggregates |

## Creating a Resource

1. Click **Add Resource** (or **New Resource**)
2. Fill in:

| Field | Required | Description |
|---|---|---|
| **Resource Code** | Yes | Unique identifier |
| **Resource Name** | Yes | Descriptive name |
| **Resource Type** | Yes | Select LABOR, NON_LABOR, or MATERIAL |
| **Status** | Yes | ACTIVE (available) or INACTIVE (not available) |
| **Max Units/Day** | No | Maximum availability (default: 1.0) |
| **Calendar** | No | Assign a working-time calendar |

3. Click **Save**

## Resource Allocation

Once resources are defined, they can be assigned to project activities:

1. Navigate to a project's **Resources** tab or **Activities** tab
2. Select an activity
3. Assign one or more resources with the required units
4. The scheduling engine uses resource availability and calendar data to calculate resource-constrained schedules

## Resource Levelling

When resources are over-allocated (assigned to more work than their availability allows), Bipros EPPM provides **resource levelling** tools to resolve conflicts by:

- Delaying non-critical activities to stay within resource limits
- Prioritizing critical-path activities
- Showing the impact on the project schedule

## Resource Utilization Reporting

The system generates reports showing:

| Report | Description |
|---|---|
| **Resource Histogram** | Bar chart showing resource demand vs. availability over time |
| **Utilization Rate** | Percentage of available hours actually used |
| **Over-Allocation Alerts** | Highlights periods where demand exceeds availability |
