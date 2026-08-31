---
sidebar_position: 7
title: Resource Planning & Deployment
description: How to plan, allocate, and track resources across project activities
---

# Resource Planning & Deployment

This guide covers planning resource requirements, creating resource curves, deploying resources to activities, and monitoring utilisation.

## Prerequisites

- Project and WBS are set up
- Activities exist in the schedule
- Resource master data is configured (labour designations, equipment types, materials)
- You have `RESOURCE_UPDATE` permission

## Step 1: Define Resource Requirements

1. Navigate to your project
2. Click the **Resources** tab
3. For each activity, click **Assign Resources**
4. Define requirements:

| Resource Type | Planning Fields |
|---|---|
| **Labour** | Designation, planned count, productivity norm, daily hours |
| **Equipment** | Equipment type, planned units, hourly rate, availability |
| **Materials** | Material type, planned quantity, unit, delivery schedule |

## Step 2: Apply Resource Curves

Resource curves control how resources are distributed over the activity duration:

| Curve Type | Description | Use Case |
|---|---|---|
| **Uniform** | Even distribution across duration | Standard labour |
| **Front-Loaded** | Higher at start, tapering off | Setup activities |
| **Back-Loaded** | Lower at start, increasing | Finishing activities |
| **Bell-Shaped** | Peak in the middle | Concrete pours |

### Resource Curve Formula

For a uniform curve over $n$ periods:

$$\text{Units per Period} = \frac{\text{Total Units}}{n}$$

For a front-loaded curve:

$$\text{Units}_i = \text{Total Units} \times \frac{2(n - i + 1)}{n(n + 1)}$$

Where $i$ = period index (1 to $n$).

## Step 3: Deploy Resources via DPR

Actual resource deployment is captured in the DPR:

1. Create a DPR for the reporting date
2. In the **Resource Deployment** section, record actual counts
3. The system compares actual vs. planned:

$$\text{Utilisation Rate} = \frac{\text{Actual Deployment}}{\text{Planned Deployment}} \times 100\%$$

## Step 4: Monitor Resource Utilisation

1. Navigate to **Reports > Resource Utilisation**
2. View charts showing:
   - Planned vs. Actual deployment over time
   - Capacity utilisation percentage
   - Over-allocation alerts

### Capacity Utilisation

$$\text{Capacity Utilisation} = \frac{\text{Allocated Hours}}{\text{Available Hours}} \times 100\%$$

## Step 5: Resource Leveling (Optional)

If resources are over-allocated:

1. Go to **Schedule > Resource Leveling**
2. Select the resource to level
3. Choose strategy:
   - **Leveling** — Delays activities to resolve over-allocation (may extend project)
   - **Smoothing** — Adjusts within float without extending project
4. Review the proposed schedule changes
5. Apply if acceptable

## Expected Outcome

- Resources are planned with curves and productivity norms
- Actual deployment is tracked against plans
- Utilisation reports highlight over/under-allocation
- Resource conflicts are resolved via leveling

## Related Documentation

- [Resources Overview](../resources/overview)
- [Calendars](../resources/calendars)
- [Daily Progress Report](./tracking-daily-progress)
