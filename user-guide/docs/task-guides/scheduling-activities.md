---
sidebar_position: 4
title: Scheduling Activities with CPM
description: How to create activities, set dependencies, and run CPM scheduling
---

# Scheduling Activities with CPM

This guide covers creating activities, defining dependencies, and running the **Critical Path Method (CPM)** to calculate your project schedule.

## Prerequisites

- Project and WBS are set up
- You have `SCHEDULE_UPDATE` permission
- Working calendar is configured for the project

## Step 1: Create Activities

1. Navigate to your project and click the **Activities** tab
2. Click **New Activity**
3. Fill in the activity details:

| Field | Required | Description |
|---|---|---|
| **Activity ID** | Yes | Unique code (e.g., `A1010`) |
| **Activity Name** | Yes | Descriptive name |
| **WBS Node** | Yes | The WBS work package this activity belongs to |
| **Duration** | Yes | Original duration in working days |
| **Calendar** | No | Override the default project calendar |
| **Activity Type** | Yes | `Task Dependent`, `Resource Dependent`, or `Milestone` |

## Step 2: Define Dependencies

1. In the Activities tab, click the **Dependencies** sub-tab
2. For each activity, define predecessor relationships:

| Relationship Type | Symbol | Description |
|---|---|---|
| **Finish-to-Start** | FS | Successor cannot start until predecessor finishes (default) |
| **Start-to-Start** | SS | Successor cannot start until predecessor starts |
| **Finish-to-Finish** | FF | Successor cannot finish until predecessor finishes |
| **Start-to-Finish** | SF | Successor cannot finish until predecessor starts (rare) |

3. Set **Lag** (positive delay) or **Lead** (negative lag) in days

### Example Dependency Chain

```
Activity A (Foundation) --FS--> Activity B (Walls) --FS+2--> Activity C (Roof)
```

Activity B starts after A finishes. Activity C starts 2 days after B finishes.

## Step 3: Assign Resources (Optional)

1. Open an activity and click the **Resources** tab
2. Add labour, equipment, or material resources
3. Set allocation units (e.g., 100% = full-time)

## Step 4: Run CPM Scheduling

1. Click **Schedule > Calculate** in the toolbar
2. The system runs the CPM algorithm:
   - **Forward Pass** — Calculates Early Start ($ES$) and Early Finish ($EF$)
   - **Backward Pass** — Calculates Late Start ($LS$) and Late Finish ($LF$)
   - **Float Calculation** — Determines Total Float ($TF$) and Free Float ($FF$)

### CPM Formulas

**Forward Pass:**
- $ES = \max(\text{Predecessor } EF + \text{lag})$
- $EF = ES + \text{Duration}$

**Backward Pass:**
- $LF = \min(\text{Successor } LS - \text{lag})$
- $LS = LF - \text{Duration}$

**Float:**
- $TF = LS - ES$ (or $LF - EF$)
- $FF = \text{Successor } ES - EF - \text{lag}$

## Step 5: Review the Schedule

After CPM calculation:

1. **Gantt Chart** — Visual timeline with bars for each activity
2. **Critical Path** — Activities with $TF = 0$ are highlighted in red
3. **Float Analysis** — Non-critical activities show available slack

## Expected Outcome

- All activities have calculated $ES$, $EF$, $LS$, $LF$, $TF$, and $FF$
- The critical path is identified (longest path, $TF = 0$)
- Project finish date is determined by the last critical activity

## Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| Circular dependency | Activity A depends on B, B depends on A | Remove or correct the dependency loop |
| Negative float | Constraints force dates earlier than CPM allows | Remove hard constraints or adjust deadlines |
| Missing dates | Calendar has no working days | Configure project calendar in Resources > Calendars |

## Related Documentation

- [Activities](../projects/activities)
- [Schedule Compression](./running-schedule-compression)
- [CPM Deep Dive](../module-reference/activities-scheduling/)
