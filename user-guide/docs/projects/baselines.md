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

## Baseline Types and Slots

A project can hold many stored baselines, of which up to three are **assigned to slots** at any time. Slots are independent — assigning to `SECONDARY` does **not** unset `PRIMARY`. Variance views and the Gantt overlay pick up whichever baseline currently sits in the slot you choose.

| Type / Slot | Purpose |
|---|---|
| **PRIMARY** | The main approved baseline — typically set at project start after the schedule is approved |
| **SECONDARY** | An alternative baseline for comparison (e.g. previous re-baseline) |
| **TERTIARY** | A third comparison slot for what-if or scenario analysis |
| **PROJECT** | A general-purpose stored baseline not tied to a slot until assigned |

## Creating a Baseline

1. Click **Create Baseline** (or **New Baseline**)
2. Fill in:

| Field | Required | Description |
|---|---|---|
| **Baseline Name** | Yes | Descriptive name (e.g. "Approved Schedule — April 2026") |
| **Baseline Type** | Yes | Select PRIMARY, SECONDARY, TERTIARY, or PROJECT |
| **Description** | No | Notes about why this baseline is being created |
| **Source Project** | No | Pick another project to snapshot instead of the current one (P6 "save as a copy") |

3. Click **Save**

The system captures a snapshot of all current activity dates, durations, relationships, costs, resource assignments, and expenses.

:::tip Assign vs Create
Creating a baseline does **not** automatically assign it to a slot. After saving, use **Assign** (or the slot pickers in the variance dashboard / Gantt) to place the baseline in `PRIMARY`, `SECONDARY`, or `TERTIARY`.
:::

## Assigning a Baseline to a Slot

Open the **Assign** page from the Baselines tab, or use the slot picker on the variance dashboard or the Gantt baseline overlay.

1. Pick the slot (`PRIMARY` / `SECONDARY` / `TERTIARY`)
2. Pick the baseline you want in that slot
3. Save

Slots are independent — assigning a new baseline to one slot leaves the other two untouched. Use **Clear Slot** to detach without deleting the baseline.

## Updating a Baseline (Selective)

When only part of the plan has changed (e.g. a few activities have been re-sequenced after a controlled scope change), you can refresh **just those activities** in the existing baseline rather than starting over.

1. On the Baselines tab, click **Update Baseline** on the row
2. Optionally narrow the scope: specific activity IDs, critical-only, milestones-only, statuses, planned-start window
3. Pick which fields to refresh: dates, durations, relationships, resource costs, expense costs
4. Confirm

The rest of the baseline snapshot is preserved.

## Restoring from a Baseline

When the live schedule has drifted in a way you want to revert, use **Restore** to copy planned dates, durations, and relationships from the baseline back onto the project.

:::warning Destructive — actuals are preserved
Restore overwrites planned dates, durations, and relationships on the live project. **Actual start/finish, % complete, and posted costs are kept intact.** The action is audit-logged but cannot be undone. Re-run CPM after a restore.
:::

1. On the Baselines tab, click **Restore** on the baseline row
2. Read and confirm the destructive-action prompt
3. The system overwrites planned data; actuals stay where they are
4. Re-run CPM to recompute floats and the critical path

## Re-baseline Banner

When a Variation Order is approved (or another listener flags that the baseline is materially out of date), a yellow **Re-baseline required** banner appears on the project header. This is an operator nudge — the baseline itself is **not** changed automatically. Decide whether to:

- **Update** the existing baseline selectively (UC-BL-04), or
- **Create a new baseline** and re-assign slots, or
- **Restore** to revert recent drift (UC-BL-05).

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
