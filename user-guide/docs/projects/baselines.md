---
sidebar_position: 21
title: Baselines
description: Create schedule snapshots for variance tracking and performance measurement
---

# Baselines

A **Baseline** is a frozen snapshot of the project schedule taken at a specific point in time. It serves as the reference point against which all future schedule changes are measured. Baselines are essential for variance tracking and Earned Value Management (EVM).

## Accessing Baselines

Navigate to a project and click the **Baselines** tab (available via the tab bar or query parameter).

![Baselines Tab](/img/screenshots/39-project-baselines.png)

## Baseline Types

Bipros EPPM supports multiple baselines per project:

| Type | Purpose |
|---|---|
| **PRIMARY** | The main approved baseline — typically set at project start after the schedule is approved |
| **SECONDARY** | An alternative baseline for comparison (e.g., after a major re-baseline exercise) |
| **WHAT_IF** | A hypothetical baseline used for scenario analysis |

## Creating a Baseline

1. Click **Create Baseline** (or **New Baseline**)
2. Fill in:

| Field | Required | Description |
|---|---|---|
| **Baseline Name** | Yes | Descriptive name (e.g., "Approved Schedule — April 2026") |
| **Baseline Type** | Yes | Select PRIMARY, SECONDARY, or WHAT_IF |
| **Description** | No | Notes about why this baseline is being created |

3. Click **Save**

The system captures a snapshot of all current activity dates, durations, costs, and resource assignments.

## Baseline vs. Current Comparison

Once a baseline exists, you can compare it against the current schedule:

| Metric | Baseline Value | Current Value | Variance |
|---|---|---|---|
| **Start Date** | Original planned start | Current planned or actual start | Difference in days |
| **Finish Date** | Original planned finish | Current planned or actual finish | Difference in days |
| **Duration** | Original duration | Current duration | Difference in days |
| **Cost** | Original budget | Current budget or actual | Difference in currency |

## When to Create Baselines

- **Project kick-off** — Create the PRIMARY baseline after the schedule is approved
- **After major changes** — Create a new SECONDARY baseline when significant scope or schedule changes are approved
- **Re-baseline exercises** — When the original plan is no longer a meaningful comparison
- **Scenario analysis** — Create WHAT_IF baselines to explore alternative schedules

:::tip
Once a baseline is set, it should not be modified. If the project plan changes significantly, create a new baseline rather than editing the existing one.
:::
