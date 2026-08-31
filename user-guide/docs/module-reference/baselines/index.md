---
sidebar_position: 1
title: Baselines — Deep Dive
description: Technical reference for baseline creation, comparison, restoration, and slot assignment
---

# Baselines — Deep Dive

## Overview

A **baseline** is a snapshot of the approved project plan (schedule, costs, and resource assignments) against which actual performance is measured. Bipros EPPM supports the full Primavera-P6-style baseline lifecycle: create, assign to a slot, compare, selectively update, and restore.

Each project can hold many stored baselines, of which up to three may be **assigned to slots** (`PRIMARY`, `SECONDARY`, `TERTIARY`) at any time. Slots are independent — assigning a baseline to `SECONDARY` does **not** unset whatever is in `PRIMARY`. Variance dashboards and the Gantt chart pick up the baseline currently sitting in the slot the user selects.

## Actors & Roles

| Actor | Role |
|---|---|
| **Project Manager** | Creates, assigns, restores, and deletes baselines; resolves re-baseline prompts |
| **Planning Engineer** | Ensures the schedule is ready before a baseline is taken; runs the variance comparison |
| **Cost Engineer** | Ensures the budget is ready for baseline; reviews cost variance |
| **Admin** | Same baseline rights as Project Manager (RBAC) |

## Core Concepts

### Baseline Slots

| Slot | Typical Use |
|---|---|
| **PRIMARY** | The contract / approved baseline — the one EVM and S-curves use by default |
| **SECONDARY** | An alternative comparison point (e.g. previous re-baseline) |
| **TERTIARY** | A third comparison slot for what-if or board-presentation scenarios |
| *(unassigned)* | Stored baselines not currently in a slot still exist and can be assigned later |

The legacy `/activate` endpoint maps to assigning the baseline to `PRIMARY` and is kept for one release while clients migrate to the explicit slot endpoints.

### Re-baseline Banner

When events that materially invalidate the baseline are processed (notably an approved Variation Order via `VariationOrderApprovedListener`), the project header surfaces a yellow **Re-baseline required** banner. This is an operator nudge — the baseline is not changed automatically.

### What a Baseline Captures

| Snapshot Layer | Stored In | Captures |
|---|---|---|
| Header | `Baseline` | Name, description, type (`PROJECT` / `PRIMARY` / `SECONDARY` / `TERTIARY`), date, total cost, project duration, start/finish |
| Activities | `BaselineActivity` | Early/late start/finish, original duration, remaining duration, total/free float, planned cost, % complete |
| Relationships | `BaselineRelationship` | Predecessor/successor links and lag |
| WBS | `BaselineWbs` | WBS hierarchy and codes at the moment of snapshot |
| Resource assignments | `BaselineResourceAssignment` | Budgeted units and budgeted cost per assignment |
| Expenses | `BaselineExpense` | Non-resource project expenses |

## Use Cases

### UC-BL-01: Create Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-BL-01 |
| **Name** | Create Baseline |
| **Actor** | Project Manager |
| **Precondition** | CPM schedule is calculated and reviewed; budget is finalised |
| **Trigger** | User clicks **Create Baseline** on the project's Baselines tab |

**Main Flow:**
1. User enters baseline **name** (required), **type** (`PRIMARY` / `SECONDARY` / `TERTIARY` / `PROJECT`), and optional **description**
2. *(Optional)* user picks `sourceProjectId` to snapshot another project instead of the current one (P6's "save as a copy" workflow — variance comparison only matches when activity IDs overlap)
3. System snapshots activities, relationships, WBS, resource assignments, and expenses
4. System persists the baseline with timestamp and current user

**Postcondition:** A new baseline exists in the project's baseline list. It is **not** auto-assigned to a slot — the user assigns it explicitly.

### UC-BL-02: Assign Baseline to Slot

| Attribute | Value |
|---|---|
| **ID** | UC-BL-02 |
| **Name** | Assign Baseline to Slot |
| **Actor** | Project Manager |
| **Precondition** | At least one stored baseline exists |
| **Trigger** | User opens the **Assign** page (or uses slot pickers in the variance dashboard / Gantt) |

**Main Flow:**
1. User picks a slot (`PRIMARY` / `SECONDARY` / `TERTIARY`)
2. User picks the baseline to occupy that slot
3. System replaces whatever was in that slot — other slots are untouched

**Postcondition:** Variance views and the Gantt baseline overlay use the newly assigned baseline for the chosen slot.

### UC-BL-03: Compare Current vs Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-BL-03 |
| **Name** | Compare Current vs Baseline |
| **Actor** | Planning Engineer, Project Manager |
| **Precondition** | A baseline is assigned to at least one slot |
| **Trigger** | User opens the **Variance Dashboard** or **Schedule Comparison** view |

**Main Flow:**
1. User selects which slot's baseline to compare against
2. System returns per-activity variance: start variance (days), finish variance (days), duration variance, cost variance
3. Schedule comparison classifies each row as `ADDED`, `DELETED`, `CHANGED`, or `UNCHANGED`
4. User reviews variance highlights and exports the report

**Postcondition:** Variance is visible per activity; aggregated SV / CV roll up to project EVM.

### UC-BL-04: Selectively Update Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-BL-04 |
| **Name** | Selectively Update Baseline |
| **Actor** | Project Manager |
| **Precondition** | A baseline exists; the project plan has changed in a controlled way (e.g. approved scope addition affecting only some activities) |
| **Trigger** | User clicks **Update Baseline** on a baseline row |

**Main Flow:**
1. User narrows the update with optional filters: specific activity IDs, critical-only, milestones-only, statuses, planned-start window
2. User picks which fields to refresh: dates, durations, relationships, resource costs, expense costs (any combination)
3. System overwrites only the selected fields on the matching activities; everything else in the baseline is preserved
4. System logs the update for audit

**Postcondition:** The baseline reflects the controlled changes without invalidating the rest of the snapshot. Use this instead of creating a brand-new baseline when only a subset of work has changed.

### UC-BL-05: Restore Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-BL-05 |
| **Name** | Restore Baseline |
| **Actor** | Project Manager |
| **Precondition** | A baseline exists; the live schedule has drifted in a way the planner wants to revert |
| **Trigger** | User clicks **Restore** on a baseline row and confirms the destructive prompt |

**Main Flow:**
1. UI shows a confirmation prompt explaining that planned dates, durations, and relationships will be overwritten
2. On confirm, system overwrites the live project's planned dates, durations, and relationships from the baseline snapshot
3. **Actuals are preserved** — actual start/finish, % complete, and posted costs are not touched
4. System writes an audit-log entry; the action is **not** undoable

**Postcondition:** The live schedule's planned side matches the baseline; actuals remain intact. CPM should be re-run.

### UC-BL-06: Clear / Delete Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-BL-06 |
| **Name** | Clear Slot or Delete Baseline |
| **Actor** | Project Manager |
| **Precondition** | A baseline is assigned to a slot, or a stored baseline is no longer needed |
| **Trigger** | User clicks **Clear Slot** (slot detach) or **Delete** (full removal) |

**Main Flow:**
1. **Clear Slot** removes the slot assignment but keeps the baseline available for re-assignment (idempotent — clearing an empty slot is a no-op)
2. **Delete** removes the baseline and all its captured activities, relationships, WBS, resource assignments, and expenses

**Postcondition:** Either the slot is empty (data preserved) or the baseline is gone (data removed).

## Variance Calculation

$$\text{Schedule Variance (Start)} = \text{Current Start} - \text{Baseline Start}$$

$$\text{Schedule Variance (Finish)} = \text{Current Finish} - \text{Baseline Finish}$$

$$\text{Duration Variance} = \text{Current Duration} - \text{Baseline Duration}$$

$$\text{Cost Variance} = \text{Current Planned Cost} - \text{Baseline Planned Cost}$$

## Business Rules

1. **Slot independence** — Assigning a baseline to one slot never modifies any other slot.
2. **Restore is destructive but actual-safe** — Planned dates, durations, and relationships are overwritten; actuals (start, finish, % complete, posted costs) are preserved.
3. **Selective update vs new baseline** — Use *Update Baseline* with filters for controlled, scoped revisions; create a new baseline for wholesale re-baselining.
4. **VO triggers a re-baseline nudge** — When a VO is approved, the project header shows a yellow banner. The baseline itself is unchanged until the planner acts.
5. **Audit logging** — Restore and Update operations are persisted to the audit log with the user, timestamp, and scope.

## Related Modules

- [Activities & Scheduling](../activities-scheduling/)
- [EVM](../evm/)
- [Cost Management](../cost-management/) — BOQ Commercial Chain feeds the budget side of the baseline
- [Baselines task guide](../../projects/baselines)
