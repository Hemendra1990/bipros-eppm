---
sidebar_position: 1
title: Resource Management — Deep Dive
description: Technical reference for resource planning, curves, leveling, and productivity norms
---

# Resource Management — Deep Dive

## Overview

The Resource Management module handles labour, equipment, and material resources across projects. It includes resource curves, leveling algorithms, productivity norms, and deployment tracking.

## Actors & Roles

| Actor | Role in This Module |
|---|---|
| **Resource Manager** | Primary user — plans deployment, monitors utilisation |
| **Planning Engineer** | Assigns resources to activities, sets curves |
| **Site Engineer** | Records actual deployment in DPR |
| **Admin** | Manages resource master data, designations, rates |

## Use Cases

### UC-RES-01: Assign Resource to Activity

| Attribute | Value |
|---|---|
| **ID** | UC-RES-01 |
| **Name** | Assign Resource to Activity |
| **Actor** | Planning Engineer |
| **Precondition** | Activity exists, resource exists in master |
| **Trigger** | User clicks "Assign Resource" |

**Main Flow:**
1. User selects resource type (Labour / Equipment / Material)
2. User selects specific resource
3. User sets planned quantity/units
4. User selects resource curve
5. System distributes units across activity duration
6. System checks for over-allocation

**Postcondition:** Resource is assigned to activity |

### UC-RES-02: Run Resource Leveling

| Attribute | Value |
|---|---|
| **ID** | UC-RES-02 |
| **Name** | Run Resource Leveling |
| **Actor** | Planning Engineer |
| **Precondition** | Resources are assigned, schedule is calculated |
| **Trigger** | User clicks "Level Resources" |

**Main Flow:**
1. System identifies over-allocated resources
2. System applies leveling algorithm
3. System proposes delayed activity start dates
4. User reviews impact on project finish date
5. User accepts or rejects changes

**Postcondition:** Over-allocations are resolved (may extend schedule) |

## Resource Curves

### Uniform Curve

$$U_i = \frac{Q}{n}$$

Where:
- $U_i$ = Units in period $i$
- $Q$ = Total units
- $n$ = Number of periods

### Front-Loaded Curve

$$U_i = Q \times \frac{2(n - i + 1)}{n(n + 1)}$$

### Back-Loaded Curve

$$U_i = Q \times \frac{2i}{n(n + 1)}$$

### Bell-Shaped (Triangular) Curve

$$U_i = Q \times \frac{1 - |\frac{2i - n - 1}{n}|}{\sum_{j=1}^{n} (1 - |\frac{2j - n - 1}{n}|)}$$

## Resource Leveling Algorithm

Bipros EPPM uses a **priority-based leveling** algorithm:

1. **Identify over-allocations** — Find periods where demand > availability
2. **Sort activities by priority** — Total Float (ascending), then ID
3. **Delay lowest-priority activities** — Move activities within their float
4. **Recalculate schedule** — Update ES/EF for delayed activities
5. **Iterate** — Repeat until no over-allocations remain

### Resource Smoothing

Smoothing adjusts resource allocation **without extending the project**:

- Only uses available float ($TF > 0$)
- Never extends the project finish date
- May not fully resolve all over-allocations

## Productivity Norms

Productivity norms define expected output per unit of resource:

$$\text{Planned Output} = \text{Resource Count} \times \text{Hours} \times \text{Productivity Norm}$$

$$\text{Actual Output} = \text{Resource Count} \times \text{Hours} \times \text{Actual Productivity}$$

$$\text{Productivity Variance} = \frac{\text{Actual Productivity}}{\text{Planned Productivity}} \times 100\%$$

## Capacity Utilisation

$$\text{Capacity Utilisation} = \frac{\text{Allocated Hours}}{\text{Available Hours}} \times 100\%$$

| Utilisation | Status |
|---|---|
| < 60% | Under-utilised |
| 60–85% | Optimal |
| 85–100% | High (monitor) |
| > 100% | Over-allocated (action required) |

## Related Modules

- [Resource Planning Task Guide](../../task-guides/resource-planning-deployment)
- [Activities & Scheduling](../activities-scheduling/)
- [DPR Tracking](../../task-guides/tracking-daily-progress)
