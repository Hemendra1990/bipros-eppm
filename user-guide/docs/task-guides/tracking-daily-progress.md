---
sidebar_position: 5
title: Tracking Daily Progress (DPR)
description: How to create and manage Daily Progress Reports for field operations
---

# Tracking Daily Progress (DPR)

The **Daily Progress Report (DPR)** captures daily site activities, resource deployment, weather conditions, and output quantities. This guide covers the complete DPR workflow.

## Prerequisites

- Project is in `ACTIVE` status
- Activities exist in the schedule
- You have `DPR_CREATE` permission (typically Site Engineer)

## Step 1: Create a New DPR

1. Navigate to your project
2. Click the **DPR** tab
3. Click **New DPR**
4. Select the **Report Date** (defaults to today)

## Step 2: Record Daily Weather

1. In the DPR form, expand the **Weather** section
2. Enter:
   - **Morning Weather** — e.g., Clear, Cloudy, Rainy
   - **Afternoon Weather**
   - **Temperature** (min/max in °C)
   - **Remarks** — Any weather-related impacts

## Step 3: Log Resource Deployment

1. Expand the **Resource Deployment** section
2. For each resource type, record:

| Resource Type | Fields to Record |
|---|---|
| **Labour** | Designation, count, hours worked |
| **Equipment** | Equipment type, count, hours operated |
| **Materials** | Material type, quantity received, quantity consumed |

3. Click **Add Row** for each resource entry

## Step 4: Record Activity Outputs

1. Expand the **Activity Outputs** section
2. Select the **Activity** from the dropdown
3. Enter:
   - **Planned Quantity** — Auto-populated from BOQ
   - **Actual Quantity** — What was achieved today
   - **Unit** — Auto-populated (e.g., cum, sqm, RMT)
   - **Cumulative Quantity** — Auto-calculated

### Output Calculation

$$\text{Cumulative Quantity} = \sum_{i=1}^{n} \text{Actual Quantity}_i$$

Where $n$ = number of DPR days for this activity.

## Step 5: Enter Next Day Plan

1. Expand the **Next Day Plan** section
2. List planned activities for the next working day
3. Identify any constraints or risks

## Step 6: Save and Submit

1. Review all entries for accuracy
2. Click **Save Draft** to save without finalising
3. Click **Submit** to finalise the DPR (locks editing)

## Expected Outcome

- DPR is saved with a unique report number
- Activity outputs update cumulative progress
- EVM metrics (EV) recalculate based on actual completion
- Resource deployment data feeds into resource utilisation reports

## Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| Activity not in dropdown | Activity not assigned to current WBS | Check WBS assignment |
| Planned quantity is zero | No BOQ linked to activity | Link BOQ item in Activity details |
| Cannot submit | Missing required fields | Fill in all mandatory sections |

## Related Documentation

- [Equipment Logs](../projects/equipment-logs)
- [Labour Returns](../projects/labour-returns)
- [Material Reconciliation](../projects/material-reconciliation)
- [EVM](../projects/evm)
