---
sidebar_position: 11
title: Earned Value Management (EVM)
description: Track project cost and schedule performance with PV, EV, AC, CPI, and SPI
---

# Earned Value Management (EVM)

**Earned Value Management (EVM)** is a project management methodology that integrates scope, schedule, and cost to measure project performance. Bipros EPPM computes EVM metrics automatically based on your scheduled activities, actual progress, and cost data.

## Accessing EVM

Navigate to a project and click the **EVM** tab.

![EVM Tab](/img/screenshots/38-project-evm.png)

## Core EVM Metrics

### Fundamental Values

| Metric | Abbreviation | Formula | Description |
|---|---|---|---|
| **Planned Value** | PV | Budget × Planned % Complete | The authorized budget assigned to scheduled work — how much work *should* be done by now |
| **Earned Value** | EV | Budget × Actual % Complete | The value of the work actually completed — how much work *has been* done |
| **Actual Cost** | AC | Sum of actual expenditures | The actual cost incurred for the work performed — how much has been *spent* |

### Variance Indicators

| Metric | Abbreviation | Formula | Interpretation |
|---|---|---|---|
| **Cost Variance** | CV | EV - AC | Positive = under budget; Negative = over budget |
| **Schedule Variance** | SV | EV - PV | Positive = ahead of schedule; Negative = behind schedule |

### Performance Indices

| Metric | Abbreviation | Formula | Interpretation |
|---|---|---|---|
| **Cost Performance Index** | CPI | EV / AC | Above 1.0 = under budget; Below 1.0 = over budget; 1.0 = on budget |
| **Schedule Performance Index** | SPI | EV / PV | Above 1.0 = ahead of schedule; Below 1.0 = behind schedule; 1.0 = on schedule |

### Forecasting Metrics

| Metric | Abbreviation | Formula | Description |
|---|---|---|---|
| **Budget at Completion** | BAC | Total project budget | The total authorized budget for the project |
| **Estimate at Completion** | EAC | BAC / CPI | Forecast total cost based on current performance |
| **Estimate to Complete** | ETC | EAC - AC | How much more is expected to be spent |
| **Variance at Completion** | VAC | BAC - EAC | Expected total cost variance at project end |

## S-Curve Chart

The EVM tab typically includes an **S-Curve** visualization showing three lines plotted over time:

| Line | Colour (typical) | Data |
|---|---|---|
| **PV (Planned Value)** | Blue | Cumulative planned value over time |
| **EV (Earned Value)** | Green | Cumulative earned value over time |
| **AC (Actual Cost)** | Red | Cumulative actual cost over time |

### Reading the S-Curve

- **EV above PV** → Ahead of schedule
- **EV below PV** → Behind schedule
- **AC above EV** → Over budget
- **AC below EV** → Under budget

## How EVM Data Is Calculated

1. **PV** is derived from the baselined schedule and budget allocation to activities
2. **EV** is calculated as the budget multiplied by the actual physical completion percentage
3. **AC** comes from actual expenditure recorded against activities, contracts, or RA bills
4. Performance indices are automatically computed from these three values

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| EVM | Earned Value Management |
| PV | Planned Value |
| EV | Earned Value |
| AC | Actual Cost |
| CV | Cost Variance |
| SV | Schedule Variance |
| CPI | Cost Performance Index |
| SPI | Schedule Performance Index |
| BAC | Budget at Completion |
| EAC | Estimate at Completion |
| ETC | Estimate to Complete |
| VAC | Variance at Completion |
