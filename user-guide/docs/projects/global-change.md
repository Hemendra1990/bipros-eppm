---
sidebar_position: 22
title: Global Change
description: Apply bulk changes to activities across the project
---

# Global Change

The **Global Change** module allows you to apply bulk modifications to multiple activities at once, rather than editing each activity individually. This is useful for sweeping changes like adjusting all durations by a percentage or shifting all dates.

## Accessing Global Change

Navigate to a project, then select **Global Change** from the **More** dropdown menu.

![Global Change](/img/screenshots/36-project-global-change.png)

## Features

### Bulk Operations

| Operation | Description |
|---|---|
| **Duration Adjustment** | Increase or decrease durations by a fixed amount or percentage |
| **Date Shift** | Move all start/finish dates forward or backward by a number of days |
| **Status Update** | Change the status of multiple activities at once |
| **Resource Reassignment** | Reassign resources across multiple activities |

### Change Tracking

All global changes are logged with:

- **Date and time** of the change
- **User** who made the change
- **Change description** — what was modified
- **Affected activities** — list of activities impacted
- **Before/after values** — what changed

### Impact Assessment

Before applying a global change, the module shows:

- Number of activities that will be affected
- Preview of the changes
- Impact on the critical path
- Impact on project finish date

:::caution
Global changes affect multiple activities simultaneously and cannot be easily undone. Always review the impact assessment before applying changes. Consider creating a baseline before making significant global changes.
:::
