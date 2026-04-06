---
sidebar_position: 3
title: Quick Start Guide
description: Create your first project in Bipros EPPM in five minutes
---

# Quick Start Guide

This guide walks you through creating a project, adding activities, and viewing your first schedule — all in about five minutes.

## Step 1: Sign In

1. Open your browser and navigate to the Bipros EPPM URL (e.g., `http://localhost:3000`)
2. Enter your **Username** and **Password** on the Sign In page
3. Click **Sign in**

You will be redirected to the Home Dashboard.

## Step 2: Create a New Project

1. Click **Projects** in the sidebar (second icon from the top)
2. Click the **New Project** button in the top-right corner of the Projects list

![Projects List](/img/screenshots/02-projects-list.png)

3. Fill in the project details:

| Field | Example Value |
|---|---|
| **Project Code** | `PROJ-001` |
| **Project Name** | `Highway Extension Phase 1` |
| **Status** | `PLANNED` |
| **Start Date** | Today's date |
| **Finish Date** | A date 6 months from now |
| **Priority** | `HIGH` |

4. Click **Create** to save the project

## Step 3: Define the Work Breakdown Structure

1. From the project detail page, click the **WBS** tab
2. Click **Add WBS Node** to create your first level of work breakdown
3. Enter a **WBS Code** (e.g., `1.0`) and **Name** (e.g., `Earthworks`)
4. Add child nodes to create a hierarchy:
   - `1.1` — Site Clearing
   - `1.2` — Excavation
   - `1.3` — Embankment

![WBS Tab](/img/screenshots/37-project-wbs.png)

## Step 4: Add Activities

1. Click the **Activities** tab
2. Click **New Activity** to add your first activity
3. Fill in the activity form:

| Field | Example Value |
|---|---|
| **Activity Code** | `A1010` |
| **Activity Name** | `Mobilize Equipment` |
| **Duration** | `5` (days) |
| **Planned Start** | Project start date |

4. Add more activities for your WBS nodes
5. Set **dependencies** between activities to define the logical sequence

![Activities Tab](/img/screenshots/21-project-activities.png)

## Step 5: Run the Schedule

1. From the Activities tab, click **Run Schedule** (or **Calculate**)
2. The CPM engine computes:
   - **Early Start / Early Finish** dates for each activity
   - **Float** (slack time) for non-critical activities
   - The **Critical Path** — the longest sequence of dependent activities

## Step 6: View Your Dashboard

1. Click **Dashboard** in the sidebar (first icon) to return to the home page
2. You will see your new project listed under **Recent Projects**
3. The **Project Status** cards show the count of Planned, Active, and Completed projects

![Home Dashboard](/img/screenshots/01-dashboard.png)

## Step 7: Explore Further

Now that you have a project set up, explore these features:

| Feature | Where to Find It |
|---|---|
| Assign resources to activities | Project > **Resources** tab |
| Track costs and earned value | Project > **EVM** tab |
| Create a project baseline | Project > **Baselines** tab |
| View schedule health metrics | Project > More > **Schedule Health** |
| Manage contracts and billing | Project > **Contracts** tab |
| Upload project documents | Project > **Documents** tab |
| View multi-tier dashboards | Sidebar > **Dashboards** |
| Generate reports | Sidebar > **Reports** |

---

For detailed documentation on each module, continue to the sections that follow in this guide.
