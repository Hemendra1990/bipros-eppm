---
sidebar_position: 1
title: Baselines — Deep Dive
description: Technical reference for schedule and cost baseline creation and comparison
---

# Baselines — Deep Dive

## Overview

A **baseline** is a snapshot of the approved project plan (schedule and cost) against which actual performance is measured. Bipros EPPM supports multiple baselines per project.

## Actors & Roles

| Actor | Role |
|---|---|
| **Project Manager** | Creates and approves baselines |
| **Planning Engineer** | Ensures schedule is ready for baseline |
| **Cost Engineer** | Ensures budget is ready for baseline |

## Use Cases

### UC-BAS-01: Create Schedule Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-BAS-01 |
| **Name** | Create Schedule Baseline |
| **Actor** | Project Manager |
| **Precondition** | CPM schedule is calculated and reviewed |
| **Trigger** | User clicks "Create Baseline" |

**Main Flow:**
1. System prompts for baseline name
2. User enters name (e.g., "Baseline 1 — Approved Plan")
3. System snapshots all activity dates, durations, and dependencies
4. System stores baseline with timestamp
5. System sets baseline as active

**Postcondition:** Baseline is stored and active |

### UC-BAS-02: Compare Current vs. Baseline

| Attribute | Value |
|---|---|
| **ID** | UC-BAS-02 |
| **Name** | Compare Current vs. Baseline |
| **Actor** | Project Manager |
| **Precondition** | Baseline exists, current schedule has changes |
| **Trigger** | User clicks "Compare Baseline" |

**Main Flow:**
1. User selects baseline to compare
2. System displays side-by-side comparison
3. System highlights variances in dates and durations
4. System shows variance report

**Postcondition:** Comparison report is displayed |

## Baseline Data Captured

| Data Element | Description |
|---|---|
| **Activity Dates** | ES, EF, LS, LF for all activities |
| **Durations** | Original duration |
| **Dependencies** | Predecessor relationships |
| **Budget** | Budget at Completion (BAC) |
| **WBS Structure** | WBS hierarchy and codes |
| **Resources** | Planned resource assignments |

## Variance Calculation

$$\text{Schedule Variance (Start)} = \text{Current ES} - \text{Baseline ES}$$

$$\text{Schedule Variance (Finish)} = \text{Current EF} - \text{Baseline EF}$$

$$\text{Duration Variance} = \text{Current Duration} - \text{Baseline Duration}$$

## Related Modules

- [Activities & Scheduling](../activities-scheduling/)
- [EVM](../evm/)
