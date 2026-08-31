---
sidebar_position: 1
title: Activities & Scheduling — Deep Dive
description: Technical reference for activities, CPM scheduling, dependencies, and float calculations
---

# Activities & Scheduling — Deep Dive

## Overview

The Activities & Scheduling module is the core planning engine of Bipros EPPM. It implements the **Critical Path Method (CPM)** to calculate activity dates, identify the critical path, and manage schedule dependencies.

## Actors & Roles

| Actor | Role in This Module |
|---|---|
| **Planning Engineer** | Primary user — creates activities, sets dependencies, runs CPM |
| **Project Manager** | Reviews schedules, approves baselines, approves compression |
| **Site Engineer** | Updates actual start/finish dates, reports progress |
| **System** | Auto-calculates CPM, detects circular dependencies |

## Use Cases

### UC-SCH-01: Create an Activity

| Attribute | Value |
|---|---|
| **ID** | UC-SCH-01 |
| **Name** | Create an Activity |
| **Actor** | Planning Engineer |
| **Precondition** | Project exists, WBS node exists |
| **Trigger** | User clicks "New Activity" |

**Main Flow:**
1. System displays activity creation form
2. User enters Activity ID, Name, WBS Node, Duration
3. User selects Activity Type and Calendar
4. System validates Activity ID uniqueness within project
5. System creates activity with default dates
6. System redirects to activity detail

**Postcondition:** Activity exists with status `Not Started` |

### UC-SCH-02: Define Dependencies

| Attribute | Value |
|---|---|
| **ID** | UC-SCH-02 |
| **Name** | Define Activity Dependencies |
| **Actor** | Planning Engineer |
| **Precondition** | At least two activities exist |
| **Trigger** | User adds a predecessor relationship |

**Main Flow:**
1. User selects successor activity
2. User selects predecessor activity
3. User selects relationship type (FS, SS, FF, SF)
4. User enters lag/lead in days
5. System validates no circular dependency is created
6. System saves the relationship

**Postcondition:** Dependency relationship is stored |

### UC-SCH-03: Run CPM Schedule

| Attribute | Value |
|---|---|
| **ID** | UC-SCH-03 |
| **Name** | Run CPM Schedule Calculation |
| **Actor** | Planning Engineer |
| **Precondition** | Activities and dependencies are defined |
| **Trigger** | User clicks "Calculate Schedule" |

**Main Flow:**
1. System performs forward pass to calculate ES and EF
2. System performs backward pass to calculate LS and LF
3. System calculates Total Float and Free Float
4. System identifies critical path (activities with TF = 0)
5. System updates Gantt chart and activity dates
6. System displays schedule summary

**Postcondition:** All activities have calculated dates and float values |

### UC-SCH-04: Create Schedule Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-SCH-04 |
| **Name** | Create Schedule Baseline |
| **Actor** | Project Manager |
| **Precondition** | CPM schedule is calculated and reviewed |
| **Trigger** | User clicks "Create Baseline" |

**Main Flow:**
1. System prompts for baseline name and description
2. User enters details
3. System snapshots current schedule (dates, durations, costs)
4. System stores baseline with timestamp and user
5. System marks baseline as active

**Postcondition:** Baseline is stored for future comparison |

## Core Formulas

### Forward Pass

$$ES_i = \max(EF_j + \text{lag}_{ji})$$

$$EF_i = ES_i + \text{Duration}_i$$

Where:
- $ES_i$ = Early Start of activity $i$
- $EF_i$ = Early Finish of activity $i$
- $EF_j$ = Early Finish of predecessor $j$
- $\text{lag}_{ji}$ = Lag between predecessor $j$ and successor $i$

### Backward Pass

$$LF_i = \min(LS_k - \text{lag}_{ik})$$

$$LS_i = LF_i - \text{Duration}_i$$

Where:
- $LF_i$ = Late Finish of activity $i$
- $LS_i$ = Late Start of activity $i$
- $LS_k$ = Late Start of successor $k$

### Float Calculations

$$TF_i = LS_i - ES_i = LF_i - EF_i$$

$$FF_i = \min(ES_k) - EF_i - \text{lag}_{ik}$$

Where:
- $TF_i$ = Total Float of activity $i$
- $FF_i$ = Free Float of activity $i$

### Critical Path

An activity is on the **critical path** if:

$$TF_i = 0$$

The critical path is the longest path through the network and determines the minimum project duration.

## Example Calculation

Consider three activities:

| Activity | Duration | Predecessor | Relationship |
|---|---|---|---|
| A | 5 days | — | — |
| B | 3 days | A | FS |
| C | 4 days | A | FS |

**Forward Pass:**
- $ES_A = 0$, $EF_A = 0 + 5 = 5$
- $ES_B = EF_A = 5$, $EF_B = 5 + 3 = 8$
- $ES_C = EF_A = 5$, $EF_C = 5 + 4 = 9$
- Project Duration = $\max(8, 9) = 9$ days

**Backward Pass:**
- $LF_C = 9$, $LS_C = 9 - 4 = 5$
- $LF_B = 9$, $LS_B = 9 - 3 = 6$
- $LF_A = \min(6, 5) = 5$, $LS_A = 5 - 5 = 0$

**Float:**
- $TF_A = 0 - 0 = 0$ → **Critical**
- $TF_B = 6 - 5 = 1$
- $TF_C = 5 - 5 = 0$ → **Critical**

**Critical Path:** A → C (duration = 9 days)

## Dependency Types

| Type | Code | Constraint |
|---|---|---|
| **Finish-to-Start** | FS | $ES_{successor} \geq EF_{predecessor} + \text{lag}$ |
| **Start-to-Start** | SS | $ES_{successor} \geq ES_{predecessor} + \text{lag}$ |
| **Finish-to-Finish** | FF | $LF_{successor} \geq LF_{predecessor} + \text{lag}$ |
| **Start-to-Finish** | SF | $LF_{successor} \geq ES_{predecessor} + \text{lag}$ |

## Business Rules

1. **Circular dependencies are prohibited** — The system validates and rejects any relationship that creates a cycle.
2. **Negative duration is not allowed** — Activity duration must be > 0.
3. **Milestones have zero duration** — Activities marked as milestones must have duration = 0.
4. **Critical path updates automatically** — Any change to durations or dependencies triggers CPM recalculation.
5. **Actual dates override calculated dates** — Once an activity has an actual start, the CPM uses the actual value.

## Related Modules

- [Projects & WBS](../../projects/overview)
- [Schedule Compression](../../task-guides/running-schedule-compression)
- [Baselines](../../projects/baselines)
