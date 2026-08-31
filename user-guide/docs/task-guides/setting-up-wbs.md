---
sidebar_position: 3
title: Setting Up the WBS
description: How to create and manage the Work Breakdown Structure for your project
---

# Setting Up the WBS

The **Work Breakdown Structure (WBS)** is a hierarchical decomposition of your project's scope into manageable work packages. This guide covers creating a WBS from scratch or from a template.

## Prerequisites

- Project exists and is in `PLANNED` or `ACTIVE` status
- You have `WBS_UPDATE` permission for the project
- (Optional) A WBS template is available in the system

## Understanding WBS Hierarchy

```
Project Root
├── Phase 1: Foundation
│   ├── Work Package 1.1: Site Preparation
│   │   ├── Activity 1.1.1: Clearance
│   │   └── Activity 1.1.2: Leveling
│   └── Work Package 1.2: Earthwork
├── Phase 2: Structure
│   ├── Work Package 2.1: Substructure
│   └── Work Package 2.2: Superstructure
└── Phase 3: Finishes
    └── Work Package 3.1: MEP
```

## Method 1: Manual WBS Creation

### Step 1: Open the WBS Tab

1. Navigate to your project
2. Click the **WBS** tab

### Step 2: Add WBS Nodes

1. Click **Add Node** next to the parent node
2. Enter:
   - **WBS Code** — Hierarchical code (e.g., `1.1`, `1.2.1`)
   - **Name** — Descriptive name
   - **Description** — Optional detailed description
3. Click **Save**

### Step 3: Build the Hierarchy

Continue adding nodes at each level:
- **Level 1** — Major phases (e.g., Foundation, Structure, Finishes)
- **Level 2** — Work packages (e.g., Site Preparation, Earthwork)
- **Level 3+** — Sub-work packages or direct activities

### Step 4: Assign Properties

For each WBS node, you can configure:
- **Responsible OBS** — Which organisational unit owns this node
- **Budget** — Allocated budget for this node
- **Milestone flag** — Whether this node represents a milestone

## Method 2: WBS Template

### Step 1: Select a Template

1. In the WBS tab, click **Import from Template**
2. Choose from available templates (e.g., "Road Construction", "Building Construction")

### Step 2: Customise the Template

After import:
- Rename nodes to match your project
- Add or remove nodes as needed
- Adjust budgets and assignments

## WBS Templates (Admin)

System Administrators can create reusable WBS templates:

1. Navigate to **Admin > WBS Templates**
2. Click **New Template**
3. Build the template hierarchy
4. Save for future use

See [WBS Templates](../admin/wbs-templates) for details.

## Expected Outcome

- A complete hierarchical WBS for your project
- Each node has a unique code and descriptive name
- Budgets can be allocated at any level
- Activities can be assigned to leaf nodes

## Related Documentation

- [WBS Reference](../projects/wbs)
- [Creating a Project](./creating-first-project)
- [Scheduling Activities](./scheduling-activities)
