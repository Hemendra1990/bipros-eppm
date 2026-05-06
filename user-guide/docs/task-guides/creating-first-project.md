---
sidebar_position: 2
title: Creating Your First Project
description: Step-by-step guide to creating a new project in Bipros EPPM
---

# Creating Your First Project

This guide walks you through creating a new project from scratch, including setting up the EPS node, OBS assignment, and initial project details.

## Prerequisites

- You have the `PROJECT_CREATE` permission (typically Project Manager or Admin)
- An **EPS node** exists where the project will be placed
- An **OBS node** exists to assign project responsibility

## Step-by-Step Instructions

### Step 1: Navigate to Projects

1. Click **Projects** in the left sidebar
2. Click the **New Project** button (top-right corner)

### Step 2: Fill in Basic Project Details

| Field | Required | Description |
|---|---|---|
| **Project Name** | Yes | Full name of the project (e.g., "Highway Extension Phase 1") |
| **Project Code** | Yes | Unique short code (e.g., `HWY-EXT-01`). Must be unique across the system. |
| **EPS Node** | Yes | The enterprise structure node this project belongs to |
| **OBS Node** | Yes | The organisational unit responsible for this project |
| **Project Category** | No | Category from the master list (e.g., Infrastructure, Building) |
| **Asset Class** | No | Asset classification for reporting |
| **Start Date** | Yes | Planned or actual start date |
| **Finish Date** | Yes | Planned or actual finish date |
| **Status** | Yes | `PLANNED`, `ACTIVE`, or `COMPLETED` |

### Step 3: Save the Project

1. Review all fields for accuracy
2. Click **Save**
3. The system validates the project code for uniqueness
4. Upon success, you are redirected to the **Project Detail** page

## What Happens Next

When a project is created, Bipros EPPM automatically:
- Creates a root **WBS node** for the project
- Initializes an empty **activity list**
- Sets up **default document folders**
- Creates a **project-level permission scope**

## Expected Outcome

- The project appears in the Projects list
- The project detail page opens with tabs for WBS, Activities, Cost, EVM, etc.
- The project status is `PLANNED`

## Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| "Project code already exists" | Duplicate code | Use a unique code; check existing projects |
| "EPS node is required" | No EPS selected | Create an EPS node first (Admin > Enterprise Structure) |
| "Access denied" | Missing permission | Contact your System Administrator |

## Related Documentation

- [Projects Overview](../projects/overview)
- [Project Detail View](../projects/project-detail)
- [WBS Setup](./setting-up-wbs)
- [EPS & OBS](../enterprise-structure/eps)
