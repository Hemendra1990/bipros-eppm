---
sidebar_position: 7
title: Schedule Health
description: Assess schedule quality with float distribution analysis and health scoring
---

# Schedule Health

The **Schedule Health** module provides automated analysis of your project schedule's quality. It evaluates how well the schedule is structured by examining float distribution, critical path density, and other indicators.

## Accessing Schedule Health

Navigate to a project, then select **Schedule Health** from the **More** dropdown menu.

![Schedule Health](/img/screenshots/33-project-schedule-health.png)

## Health Score

The module computes an overall **Schedule Health Score** on a scale of 0 to 100:

| Score Range | Rating | Meaning |
|---|---|---|
| 80 - 100 | **Healthy** | Schedule is well-structured with adequate float and logical relationships |
| 60 - 79 | **Acceptable** | Minor issues — some activities may lack float or have loose logic |
| 40 - 59 | **At Risk** | Significant issues — schedule may not be reliable for forecasting |
| 0 - 39 | **Critical** | Major structural problems — schedule needs immediate correction |

## Float Distribution Analysis

Float (also called slack) is the amount of time an activity can be delayed without delaying the project finish date. The schedule health module analyses float distribution across all activities:

| Float Range | Description | Implication |
|---|---|---|
| **0 days (Critical)** | No flexibility — any delay impacts the project finish | These activities define the critical path |
| **1 - 10 days (Near-Critical)** | Minimal buffer — delays here could quickly become critical | Monitor closely |
| **11 - 30 days** | Moderate buffer available | Normal scheduling flexibility |
| **Over 30 days** | Large buffer — may indicate loose logic or inactive activities | Review for accuracy |

## Risk Assessment

Based on the float analysis, the module assigns a risk level:

| Risk Level | Condition |
|---|---|
| **LOW** | Less than 20% of activities are critical |
| **MEDIUM** | 20% - 40% of activities are critical |
| **HIGH** | 40% - 60% of activities are critical |
| **CRITICAL** | More than 60% of activities are on the critical path |

## What to Look For

- **High percentage of critical activities** — The schedule is fragile; any delay cascades
- **No activities with float** — Logic may be over-constrained
- **Very high float values** — Activities may be disconnected from the network or have artificial constraints
- **Missing relationships** — Activities without predecessors or successors weaken schedule integrity

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| TF | Total Float |
| FF | Free Float |
| CPM | Critical Path Method |
