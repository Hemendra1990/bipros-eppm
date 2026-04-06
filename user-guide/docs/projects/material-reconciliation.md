---
sidebar_position: 14
title: Material Reconciliation
description: Track material stock, consumption, wastage, and period-wise reconciliation
---

# Material Reconciliation

The **Material Reconciliation** module tracks the flow of construction materials at the site level — from receipt through consumption to wastage and closing stock.

## Accessing Material Reconciliation

Navigate to a project, then select **Materials** from the **More** dropdown menu.

![Material Reconciliation](/img/screenshots/30-project-material-reconciliation.png)

## Material Reconciliation Table

| Column | Description |
|---|---|
| **Material Name** | Name of the construction material (e.g., "Cement OPC 43 Grade") |
| **Unit** | Unit of measurement (MT, cum, RMT, Bags, Nos, etc.) |
| **Opening Balance** | Stock available at the start of the reconciliation period |
| **Received** | Quantity received from suppliers during the period |
| **Consumed** | Quantity used in construction activities during the period |
| **Wastage** | Quantity lost, damaged, or wasted during the period |
| **Closing Balance** | Stock remaining at the end of the period (Opening + Received - Consumed - Wastage) |
| **Period** | The reconciliation period (e.g., week or month) |

## Adding a Material Reconciliation Entry

1. Click **Add Entry** (or **New Material Record**)
2. Fill in:

| Field | Required | Description |
|---|---|---|
| **Material Name** | Yes | Name of the material |
| **Unit** | Yes | Measurement unit |
| **Opening Balance** | Yes | Starting stock quantity |
| **Received** | Yes | Quantity received in this period |
| **Consumed** | Yes | Quantity used in construction |
| **Wastage** | No | Quantity wasted (default: 0) |
| **Period From** | Yes | Start of the reconciliation period |
| **Period To** | Yes | End of the reconciliation period |

3. Click **Save**

## Stock Variance Analysis

The system automatically identifies variances:

| Variance Type | Calculation | Action |
|---|---|---|
| **Positive Variance** | Closing > Expected Closing | Investigate — possible miscount or unreported receipts |
| **Negative Variance** | Closing is less than Expected Closing | Investigate — possible theft, unreported consumption, or measurement error |
| **High Wastage** | Wastage > Threshold % | Review work quality and material handling practices |

## Common Units of Measurement

| Abbreviation | Full Form | Used For |
|---|---|---|
| **MT** | Metric Tonnes | Steel, cement, aggregates |
| **cum** | Cubic Metres | Concrete, earthwork, sand |
| **RMT** | Running Metres | Pipes, cables, kerb stones |
| **sqm** | Square Metres | Formwork, flooring, painting |
| **Nos** | Numbers | Precast units, fixtures |
| **Bags** | Bags | Cement, plaster |
| **Ltrs** | Litres | Fuel, admixtures, paint |
