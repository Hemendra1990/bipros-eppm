---
sidebar_position: 12
title: Labour Returns
description: Daily labour deployment tracking, skill categories, and man-day calculations
---

# Labour Returns

The **Labour Returns** module tracks daily labour deployment at the site level. It records the number and type of workers deployed, enabling accurate man-day calculations and labour cost tracking.

## Accessing Labour Returns

Navigate to a project, then select **Labour Returns** from the **More** dropdown menu.

![Labour Returns](/img/screenshots/28-project-labour-returns.png)

## Labour Returns Table

| Column | Description |
|---|---|
| **Date** | The date of the labour return entry |
| **Site / Location** | The physical site or location where workers were deployed |
| **Skill Category** | Classification of the workers |
| **Head Count** | Number of workers deployed |
| **Man-Days** | Calculated as Head Count × days worked (typically 1 for daily returns) |
| **Remarks** | Notes on working conditions, weather, or special circumstances |

## Skill Categories

Workers are classified into five standard categories:

| Category | Description | Typical Roles |
|---|---|---|
| **SKILLED** | Workers with specialized trade skills | Carpenters, welders, masons, electricians |
| **SEMI_SKILLED** | Workers with some training but not fully qualified | Helper-grade workers with basic training |
| **UNSKILLED** | General labourers without specialized training | Manual labourers, helpers |
| **SUPERVISOR** | Supervisory staff overseeing work crews | Foremen, charge hands, site supervisors |
| **ENGINEER** | Professional engineers and technical staff | Site engineers, quality inspectors |

## Adding a Labour Return

1. Click **Add Labour Return** (or **New Entry**)
2. Fill in:

| Field | Required | Description |
|---|---|---|
| **Date** | Yes | Date of deployment |
| **Site / Location** | Yes | Where the workers are deployed |
| **Skill Category** | Yes | Select from the five categories above |
| **Head Count** | Yes | Number of workers |
| **Man-Days** | Auto | Calculated from head count |
| **Remarks** | No | Optional notes |

3. Click **Save**

## Deployment Summary Analytics

The module provides summarized views:

- **Total man-days** for the current period (week, month)
- **Breakdown by skill category** — see the mix of skilled vs. unskilled labour
- **Trend analysis** — track deployment levels over time
- **Site-level comparison** — compare labour deployment across different sites
