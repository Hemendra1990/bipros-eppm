---
sidebar_position: 1
title: EVM — Overview
description: Technical overview of the Earned Value Management module
---

# Earned Value Management (EVM) — Overview

## Overview

Earned Value Management (EVM) integrates scope, schedule, and cost data to measure project performance. Bipros EPPM computes EVM metrics automatically based on scheduled activities, actual progress, and cost data.

## Actors & Roles

| Actor | Role in This Module |
|---|---|
| **Cost Engineer** | Primary user — monitors EVM metrics, investigates variances |
| **Project Manager** | Reviews EVM dashboards, takes corrective action |
| **Executive** | Views high-level S-curves and performance indices |
| **System** | Auto-calculates EVM on DPR submission and cost updates |

## Use Cases

### UC-EVM-01: View EVM Dashboard

| Attribute | Value |
|---|---|
| **ID** | UC-EVM-01 |
| **Name** | View EVM Dashboard |
| **Actor** | Project Manager |
| **Precondition** | Project has baseline, DPRs exist |
| **Trigger** | User clicks EVM tab |

**Main Flow:**
1. System calculates PV, EV, AC for the project
2. System derives CV, SV, CPI, SPI
3. System displays S-curve chart
4. System shows variance table
5. System highlights activities with significant variances

**Postcondition:** User sees current EVM status |

### UC-EVM-02: Export EVM Report

| Attribute | Value |
|---|---|
| **ID** | UC-EVM-02 |
| **Name** | Export EVM Report |
| **Actor** | Cost Engineer |
| **Precondition** | EVM data is calculated |
| **Trigger** | User clicks "Export" |

**Main Flow:**
1. User selects report format (PDF, Excel)
2. User selects date range
3. System generates report with all EVM metrics
4. System initiates download

**Postcondition:** Report file is downloaded |

## Data Model

### EVM Calculation Entity

| Field | Type | Description |
|---|---|---|
| `project_id` | UUID | Reference to project |
| `calculation_date` | Date | Date of calculation |
| `pv` | BigDecimal | Planned Value |
| `ev` | BigDecimal | Earned Value |
| `ac` | BigDecimal | Actual Cost |
| `cv` | BigDecimal | Cost Variance |
| `sv` | BigDecimal | Schedule Variance |
| `cpi` | BigDecimal | Cost Performance Index |
| `spi` | BigDecimal | Schedule Performance Index |
| `bac` | BigDecimal | Budget at Completion |
| `eac` | BigDecimal | Estimate at Completion |
| `etc` | BigDecimal | Estimate to Complete |
| `vac` | BigDecimal | Variance at Completion |
| `tcpi` | BigDecimal | To-Complete Performance Index |

## Rollup Logic

EVM metrics roll up hierarchically:

```
Activity EVM
    ↓
WBS Node EVM (sum of child activities)
    ↓
Project EVM (sum of root WBS nodes)
```

At each level:
- **PV** = Sum of child PVs
- **EV** = Sum of child EVs
- **AC** = Sum of child ACs
- Derived metrics (CPI, SPI, etc.) are recalculated at each level

## Related Modules

- [EVM Formulas](./formulas)
- [EVM Techniques](./techniques)
- [Cost Management](../cost-management/)
- [DPR Tracking](../../task-guides/tracking-daily-progress)
