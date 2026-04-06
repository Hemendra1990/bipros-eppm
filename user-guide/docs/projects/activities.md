---
sidebar_position: 4
title: Activities
description: Creating, managing, and scheduling activities within a project
---

# Activities

**Activities** are the fundamental building blocks of a project schedule. Each activity represents a discrete piece of work with a defined duration, start date, and finish date. Activities are linked by dependencies to form the project's logical network.

## Accessing Activities

Navigate to a project and click the **Activities** tab in the tab bar.

![Activities Tab](/img/screenshots/21-project-activities.png)

## Activities Table

The activities list displays all activities for the current project:

| Column | Description |
|---|---|
| **Activity Code** | A unique alphanumeric code identifying this activity (e.g., `A1010`, `CW-003`) |
| **Activity Name** | Descriptive name of the activity (e.g., "Excavate foundation trench") |
| **Duration** | Planned duration in working days |
| **Planned Start** | The date the activity is planned to begin |
| **Planned Finish** | The date the activity is planned to end |
| **Early Start (ES)** | Earliest possible start date calculated by the CPM engine |
| **Early Finish (EF)** | Earliest possible finish date calculated by the CPM engine |
| **Total Float (TF)** | Number of days the activity can be delayed without delaying the project finish date. Zero float means the activity is on the **critical path**. |
| **Status** | Current execution status of the activity |

## Creating a New Activity

1. Click the **New Activity** button (or **Add Activity**)
2. Fill in the required fields:

| Field | Required | Description |
|---|---|---|
| **Activity Code** | Yes | Unique identifier within this project |
| **Activity Name** | Yes | Descriptive name of the work |
| **Duration** | Yes | Number of working days (excluding non-working days per calendar) |
| **Planned Start** | Yes | When the activity should begin |
| **WBS Element** | No | Which WBS node this activity belongs to |
| **Activity Type** | No | Task type classification |

3. Click **Save** to create the activity

## Dependencies

Activities can be linked with **predecessor-successor relationships** to define the logical sequence of work:

| Relationship Type | Code | Description |
|---|---|---|
| **Finish-to-Start** | FS | Successor cannot start until predecessor finishes (most common) |
| **Start-to-Start** | SS | Successor cannot start until predecessor starts |
| **Finish-to-Finish** | FF | Successor cannot finish until predecessor finishes |
| **Start-to-Finish** | SF | Successor cannot finish until predecessor starts (rarely used) |

## Schedule Calculation (CPM)

After defining activities and their dependencies, run the **CPM (Critical Path Method)** scheduler:

1. Click the **Run Schedule** (or **Calculate**) button
2. The engine performs:
   - **Forward Pass** — Calculates Early Start (ES) and Early Finish (EF) for each activity
   - **Backward Pass** — Calculates Late Start (LS) and Late Finish (LF) for each activity
   - **Float Calculation** — Determines Total Float (TF = LS - ES) for each activity
   - **Critical Path Identification** — Activities with TF = 0 form the critical path

3. Scheduling uses the **Retained Logic** method by default

## Look-Ahead Views

The Activities page supports filtered views for short-term planning:

| View | Description |
|---|---|
| **4-Week Look-Ahead** | Shows only activities starting or in progress within the next 4 weeks |
| **13-Week Look-Ahead** | Shows activities within the next quarter (13 weeks) |

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| CPM | Critical Path Method |
| ES | Early Start |
| EF | Early Finish |
| LS | Late Start |
| LF | Late Finish |
| TF | Total Float |
| FF | Free Float |
| FS | Finish-to-Start |
| SS | Start-to-Start |
