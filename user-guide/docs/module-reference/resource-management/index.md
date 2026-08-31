---
sidebar_position: 1
title: Resource Management — Deep Dive
description: Technical reference for resource planning, curves, leveling, and productivity norms
---

# Resource Management — Deep Dive

## Overview

The Resource Management module handles labour, equipment, and material resources across projects. It includes a three-tier resource model, resource curves, levelling algorithms, productivity norms, time-phased usage tracking, role-grouped assignment views, and capacity-utilisation reporting.

## Actors & Roles

| Actor | Role in This Module |
|---|---|
| **Resource Manager** | Primary user — plans deployment, monitors utilisation |
| **Planning Engineer** | Adds resources to the project pool, assigns to activities, sets curves |
| **Site Engineer** | Records actual deployment in DPR / Daily Outputs |
| **Admin** | Manages resource master data, manpower master (categories, skills, employment types, nationalities), productivity norms, and per-resource rates |

## Core Concepts

### Three-Tier Resource Model

Bipros EPPM organises resources into three explicit tiers. A resource cannot be assigned to an activity until it has been admitted into the project's pool — this prevents accidental cross-project bookings and lets each project apply its own rate / availability overrides.

| Tier | Entity | Scope | What it carries |
|---|---|---|---|
| **1. Master** | `Resource` | Organisation-wide | Code, name, type (LABOR / NON_LABOR / MATERIAL), role, calendar, `costPerUnit` (default rate), max units/day, status |
| **2. Project Pool** | `ProjectResource` | Per project | Reference to a master resource + optional `rateOverride`, `availabilityOverride`, `customUnit`, notes |
| **3. Assignment** | `ResourceAssignment` | Per activity | Planned units, planned/actual dates, curve, planned/actual/remaining units and cost |

```
Organisation Master (Resource)
        │
        ▼  admitted into
Project Pool (ProjectResource)         ← per-project rate / availability overrides
        │
        ▼  assigned to
Activity Assignment (ResourceAssignment)
```

**Cost-rate resolution chain (two tiers):**

1. `ProjectResource.rateOverride` — if set, wins.
2. `Resource.costPerUnit` — the master "Default Rate".

If neither is set (e.g. a role-only "open seat" slot before staffing), planned cost is reported as `null` rather than fabricated. This was a deliberate change away from `ResourceRole.defaultRate`, which is no longer used for cost rollups — a role like "Construction Manager" cannot carry a single rate because actual rates vary by experience, skill, and project.

### Manpower Master

For LABOR resources, five admin-managed master tables drive the Manpower form pickers (replacing the old hard-coded enums and JSON-textarea skill picker):

| Master | Admin URL | Notes |
|---|---|---|
| **Manpower Categories** | `/admin/manpower-categories` | Two-level: top-level categories (e.g. *Skilled*) and sub-categories (e.g. *Site Engineer*) via parent FK |
| **Employment Types** | `/admin/employment-types` | e.g. Permanent, Contract, Sub-Contract |
| **Skills** | `/admin/skills` | Single source for both Primary and Secondary skill pickers |
| **Skill Levels** | `/admin/skill-levels` | e.g. Beginner, Intermediate, Expert |
| **Nationalities** | `/admin/nationalities` | Used on the Manpower resource form |

Defaults are seeded for an Indian / South-Asian construction context (3 top-level + 28 sub-categories, 5 employment types, 18 skills, 5 skill levels, 26 nationalities). Names match the legacy enum strings so existing manpower data resolves cleanly.

## Use Cases

### UC-RES-01: Assign Resource to Activity

| Attribute | Value |
|---|---|
| **ID** | UC-RES-01 |
| **Name** | Assign Resource to Activity |
| **Actor** | Planning Engineer |
| **Precondition** | Activity exists, resource exists in master **and is in the project's pool** |
| **Trigger** | User clicks "Assign Resource" |

**Main Flow:**
1. User selects resource type (Labour / Equipment / Material)
2. User selects specific resource **from the project pool**
3. User sets planned quantity/units
4. User selects resource curve
5. System distributes units across activity duration
6. System seeds `remainingUnits` and `remainingCost` from planned values
7. System checks for over-allocation

**Postcondition:** Resource is assigned to activity; remaining values are pre-seeded so role-grouped rollups are correct before any actuals are posted. |

### UC-RES-02: Run Resource Levelling

| Attribute | Value |
|---|---|
| **ID** | UC-RES-02 |
| **Name** | Run Resource Levelling |
| **Actor** | Planning Engineer |
| **Precondition** | Resources are assigned, schedule is calculated |
| **Trigger** | User clicks "Level Resources" |

**Main Flow:**
1. System identifies over-allocated resources
2. System applies levelling algorithm
3. System proposes delayed activity start dates
4. User reviews impact on project finish date
5. User accepts or rejects changes

**Postcondition:** Over-allocations are resolved (may extend schedule) |

### UC-RES-03: Add Resource to Project Pool

| Attribute | Value |
|---|---|
| **ID** | UC-RES-03 |
| **Name** | Admit a master resource into the project pool |
| **Actor** | Planning Engineer / Resource Manager |
| **Precondition** | Resource exists in the organisation master |
| **Trigger** | User opens the project's **Resources → Pool** tab and clicks **Add to Pool** |

**Main Flow:**
1. User filters available resources by type, role, or search term
2. User selects one or more resources from the picker
3. User optionally sets `rateOverride`, `availabilityOverride`, `customUnit`, and notes per row
4. System creates `ProjectResource` rows under the unique `(projectId, resourceId)` constraint
5. The pool now appears as the source list for activity assignments and the Staff Swap dialog

**Postcondition:** Resources are reserved for the project; assignments may now reference them. |

### UC-RES-04: View Time-Phased Resource Usage

| Attribute | Value |
|---|---|
| **ID** | UC-RES-04 |
| **Name** | View planned vs actual resource usage spread by month |
| **Actor** | Resource Manager / Planning Engineer |
| **Precondition** | Project has resource assignments and (ideally) a calendar |
| **Trigger** | User opens the project's **Resource Usage** view |

**Main Flow:**
1. System loads the project calendar snapshot once (work-week + exceptions)
2. For each assignment, planned units are spread linearly across the calendar's working days; if the assignment has no planned dates, the parent activity's planned dates are used as fallback
3. Actuals are bucketed per month per (resource × activity) from `DailyActivityResourceOutput.qty_executed`
4. UI renders a three-level tree (Resource Type → Resource → Activity) on the left and a horizontally-scrolling month grid on the right
5. Each cell stacks **P** (planned units) above **A** (actual units)
6. Type-level rows blank the unit total when child resources have differing productivity units (e.g. Materials = Bag + Nos + Cum cannot be summed)
7. User optionally narrows to a date window via the `from` / `to` filters

**Postcondition:** Planned vs actual consumption is visible at type, resource, and activity granularity. |

### UC-RES-05: Review Role-Grouped Assignments with Remaining Backfill

| Attribute | Value |
|---|---|
| **ID** | UC-RES-05 |
| **Name** | Review assignments grouped by role with planned / actual / remaining rollups |
| **Actor** | Planning Engineer |
| **Precondition** | Activity has resource assignments |
| **Trigger** | User opens activity details, or the project **Resources → Assignments** tab |

**Main Flow:**
1. System derives an effective role for each assignment (assignment's `roleId`, falling back to the resource's role)
2. Assignments are grouped by role
3. Each role group shows an **X-of-Y staffed** pill plus planned / actual / remaining for both units and cost
4. Activity-level unit sums are shown only when all underlying assignments share the same productivity unit; otherwise the cell is blank to avoid misleading totals
5. Where a stored `remainingUnits` is null (legacy data pre-backfill), the UI derives it as `planned − actual` so totals stay correct

**Postcondition:** User sees how many seats per role are still open and how much work / cost remains. |

### UC-RES-06: Manage Manpower Master Data

| Attribute | Value |
|---|---|
| **ID** | UC-RES-06 |
| **Name** | Maintain manpower categories, skills, employment types, skill levels, nationalities |
| **Actor** | Admin |
| **Precondition** | User has admin permission |
| **Trigger** | User navigates to **Admin → Manpower** and selects a master screen |

**Main Flow:**
1. Admin opens one of the five master screens (categories, skills, skill levels, employment types, nationalities)
2. Admin searches, edits inline, or adds a new entry via the create form
3. For categories, admin optionally sets a parent (top-level vs sub-category) via the parent SearchableSelect
4. System persists via `/v1/admin/{master}` endpoints
5. New entries become available in the Manpower resource form's dropdowns and MultiSelect skill pickers

**Postcondition:** Dropdown options reflect the organisation's vocabulary without requiring a code change. |

### UC-RES-07: Export Capacity-Utilisation Workbook

| Attribute | Value |
|---|---|
| **ID** | UC-RES-07 |
| **Name** | Download a 5-sheet Excel workbook of monthly capacity utilisation |
| **Actor** | Resource Manager / PMO |
| **Precondition** | Project has resources, assignments, productivity norms, and Daily Outputs / DPR data for the selected month |
| **Trigger** | User opens **Reports → Capacity Utilisation** and clicks **Download Excel** |

**Main Flow:**
1. User picks a project, target month (`YYYY-MM`), and the working-day count for that month (default 26)
2. System computes per-resource utilisation: actuals come from Daily Outputs, planned norms come from the productivity-norm master with `COALESCE(output_per_man_per_day, output_per_day)` so per-person manpower norms compare against per-person `daysWorked`
3. Backend streams a `.xlsx` workbook with five sheets: **Plant Util**, **Manpower Util**, **SUMMARY**, **Daily Deployment**, **DPR**
4. Browser downloads the file as `capacity-utilization-YYYY-MM.xlsx`

**Postcondition:** Stakeholders have an offline, printable view of plant and manpower utilisation for the period. |

## Resource Curves

### Uniform Curve

$$U_i = \frac{Q}{n}$$

Where:
- $U_i$ = Units in period $i$
- $Q$ = Total units
- $n$ = Number of periods

### Front-Loaded Curve

$$U_i = Q \times \frac{2(n - i + 1)}{n(n + 1)}$$

### Back-Loaded Curve

$$U_i = Q \times \frac{2i}{n(n + 1)}$$

### Bell-Shaped (Triangular) Curve

$$U_i = Q \times \frac{1 - |\frac{2i - n - 1}{n}|}{\sum_{j=1}^{n} (1 - |\frac{2j - n - 1}{n}|)}$$

## Resource Leveling Algorithm

Bipros EPPM uses a **priority-based leveling** algorithm:

1. **Identify over-allocations** — Find periods where demand > availability
2. **Sort activities by priority** — Total Float (ascending), then ID
3. **Delay lowest-priority activities** — Move activities within their float
4. **Recalculate schedule** — Update ES/EF for delayed activities
5. **Iterate** — Repeat until no over-allocations remain

### Resource Smoothing

Smoothing adjusts resource allocation **without extending the project**:

- Only uses available float ($TF > 0$)
- Never extends the project finish date
- May not fully resolve all over-allocations

## Productivity Norms

Productivity norms define expected output per unit of resource. Norms can be set as a **type-default** (applies to all resources of a type) or as a **specific override** for one resource — the admin form's Scope radio makes this explicit.

For Manpower, two output fields are supported and the report engine prefers the per-person rate:

| Field | Meaning |
|---|---|
| `output_per_man_per_day` | Output per person per working day — preferred for capacity calculations |
| `output_per_day` | Gang / crew output per day — used when no per-person rate is set |

Capacity Utilisation uses `COALESCE(output_per_man_per_day, output_per_day)` so per-person manpower norms compare against per-person `daysWorked` captured in Daily Outputs. Equipment norms continue to use `output_per_day`. This avoids spurious under-utilisation that came from comparing gang output against person-day actuals.

$$\text{Planned Output} = \text{Resource Count} \times \text{Hours} \times \text{Productivity Norm}$$

$$\text{Actual Output} = \text{Resource Count} \times \text{Hours} \times \text{Actual Productivity}$$

$$\text{Productivity Variance} = \frac{\text{Actual Productivity}}{\text{Planned Productivity}} \times 100\%$$

## Capacity Utilisation

$$\text{Capacity Utilisation} = \frac{\text{Allocated Hours}}{\text{Available Hours}} \times 100\%$$

| Utilisation | Status |
|---|---|
| < 60% | Under-utilised |
| 60–85% | Optimal |
| 85–100% | High (monitor) |
| > 100% | Over-allocated (action required) |

## Related Modules

- [Resources Overview](../../resources/overview)
- [Resource Planning Task Guide](../../task-guides/resource-planning-deployment)
- [Activities & Scheduling](../activities-scheduling/)
- [DPR Tracking](../../task-guides/tracking-daily-progress)
- [Capacity Utilisation Report](../../reports-analytics/reports)
