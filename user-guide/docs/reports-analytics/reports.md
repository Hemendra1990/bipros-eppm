---
sidebar_position: 1
title: Reports
description: Generate S-curves, resource histograms, cash flow reports, and more
---

# Reports

The **Reports** module provides a comprehensive suite of predefined and customizable reports for project analysis and stakeholder communication.

## Accessing Reports

Click **Reports** in the sidebar.

![Reports](/img/screenshots/07-reports.png)

## Available Report Types

### S-Curve Report

The S-Curve is the most important project management visualization, plotting cumulative values over time:

| Curve | Data Source | What It Shows |
|---|---|---|
| **Planned Value (PV)** | Baselined schedule + budget | Cumulative planned expenditure over time |
| **Earned Value (EV)** | Actual progress × budget | Cumulative value of work completed |
| **Actual Cost (AC)** | Actual expenditure records | Cumulative actual spending |

**How to read the S-Curve:**
- Gap between PV and EV shows **schedule variance**
- Gap between EV and AC shows **cost variance**
- Where curves converge, performance is aligned with the plan

### Resource Histogram

A bar chart showing resource demand over time:

| Feature | Description |
|---|---|
| **Resource Allocation Bars** | Height represents resource demand in each time period |
| **Availability Line** | Horizontal line showing maximum available units |
| **Over-Allocation Zones** | Bars exceeding the availability line (highlighted in red) |
| **Resource Type Filter** | Filter by LABOR, NON_LABOR, or MATERIAL |

### Cash Flow Report

Period-wise financial tracking:

| Column | Description |
|---|---|
| **Period** | Time period (week, month, or quarter) |
| **Planned Inflow** | Expected income or fund release |
| **Planned Outflow** | Expected expenditure |
| **Actual Inflow** | Actual income received |
| **Actual Outflow** | Actual expenditure incurred |
| **Cumulative Position** | Running total of net cash position |

### Schedule Comparison Report

Side-by-side comparison of baseline versus current schedule:

| Feature | Description |
|---|---|
| **Baseline Dates** | Original planned start/finish for each activity |
| **Current Dates** | Current scheduled start/finish |
| **Variance** | Difference in days (positive = ahead, negative = behind) |
| **Critical Path Comparison** | Highlights changes to the critical path |

### Contract Status Report

| Metric | Description |
|---|---|
| **Contract Value** | Total awarded amount |
| **Work Completed** | Percentage and value of completed work |
| **Amount Billed** | Total RA bills submitted |
| **Amount Paid** | Total payments disbursed |
| **Retention Held** | Amount withheld as retention |
| **LD Applied** | Liquidated damages deducted |

### Risk Register Report

| Feature | Description |
|---|---|
| **Risk Count** | Total number of open risks by category |
| **Risk Distribution** | Breakdown by probability × impact |
| **Mitigation Progress** | Status of mitigation actions |
| **Top Risks** | Highest-scored risks requiring attention |

### Monthly Progress Report

A comprehensive periodic report including:

- Executive summary
- Key metrics overview (schedule, cost, quality, safety)
- Progress photographs
- Look-ahead activities for the next period
- Issues and escalations
- Variance analysis

### Resource Utilization Report

| Metric | Description |
|---|---|
| **Availability** | Total available hours per resource per period |
| **Allocation** | Hours assigned to activities |
| **Utilization %** | (Allocation / Availability) × 100 |
| **Over-Allocation** | Periods where allocation exceeds availability |

## Exporting Reports

Reports can be exported in various formats:

| Format | Use Case |
|---|---|
| **PDF** | Formal reporting, printing, email distribution |
| **Excel** | Data analysis, pivot tables, custom calculations |
| **CSV** | Data import into other systems |
