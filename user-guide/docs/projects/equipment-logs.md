---
sidebar_position: 13
title: Equipment Logs
description: Track equipment deployment, operating hours, breakdowns, and fuel consumption
---

# Equipment Logs

The **Equipment Logs** module tracks all equipment deployed at project sites, including operating hours, idle time, breakdowns, and fuel consumption.

## Accessing Equipment Logs

Navigate to a project, then select **Equipment Logs** from the **More** dropdown menu.

![Equipment Logs](/img/screenshots/29-project-equipment-logs.png)

## Equipment Logs Table

| Column | Description |
|---|---|
| **Date** | Date of the log entry |
| **Equipment Name** | Name or description of the equipment (e.g., "JCB 3DX Backhoe Loader") |
| **Equipment Code** | Unique identifier for the equipment |
| **Operator** | Name of the operator assigned to the equipment |
| **Status** | Current equipment status |
| **Operating Hours** | Hours the equipment was actively working |
| **Idle Hours** | Hours the equipment was available but not in use |
| **Breakdown Hours** | Hours lost due to equipment failure or maintenance |
| **Fuel Consumed** | Litres of fuel consumed |

## Equipment Status Values

| Status | Description |
|---|---|
| **WORKING** | Equipment is operational and actively deployed |
| **IDLE** | Equipment is on-site but not in use |
| **UNDER_MAINTENANCE** | Equipment is undergoing scheduled maintenance |
| **BREAKDOWN** | Equipment has failed and is awaiting repair |

## Adding an Equipment Log

1. Click **Add Equipment Log** (or **New Entry**)
2. Fill in:

| Field | Required | Description |
|---|---|---|
| **Date** | Yes | Date of the log entry |
| **Equipment Name** | Yes | Description of the equipment |
| **Equipment Code** | Yes | Unique identifier |
| **Operator** | No | Name of the assigned operator |
| **Status** | Yes | Select from WORKING, IDLE, UNDER_MAINTENANCE, or BREAKDOWN |
| **Operating Hours** | Yes | Productive hours (typically 0-24) |
| **Idle Hours** | No | Non-productive available hours |
| **Breakdown Hours** | No | Hours lost to breakdown |
| **Fuel Consumed** | No | Fuel in litres |

3. Click **Save**

## Utilization Summary

The module provides summary analytics:

| Metric | Calculation | Purpose |
|---|---|---|
| **Utilization Rate** | Operating Hours / (Operating + Idle + Breakdown) | Measures how effectively equipment is being used |
| **Availability Rate** | (Operating + Idle) / Total Hours | Measures equipment reliability |
| **Breakdown Rate** | Breakdown Hours / Total Hours | Indicates maintenance quality |
| **Fuel Efficiency** | Fuel Consumed / Operating Hours | Tracks fuel consumption per operating hour |
