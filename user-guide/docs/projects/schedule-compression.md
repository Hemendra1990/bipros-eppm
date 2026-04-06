---
sidebar_position: 8
title: Schedule Compression
description: Fast-tracking and crashing analysis to shorten the project schedule
---

# Schedule Compression

The **Schedule Compression** module provides tools to analyse how the project schedule can be shortened. It offers two standard techniques from project management: **Fast-Tracking** and **Crashing**.

## Accessing Schedule Compression

Navigate to a project, then select **Schedule Compression** from the **More** dropdown menu.

![Schedule Compression](/img/screenshots/32-project-schedule-compression.png)

## Fast-Tracking

Fast-tracking means performing activities in parallel that were originally planned to be sequential.

### How It Works

1. The module identifies activities with **Finish-to-Start (FS)** relationships that could potentially overlap
2. It analyses which activities can safely run in parallel
3. It calculates the **potential time savings** from overlapping them

### Key Considerations

| Factor | Description |
|---|---|
| **Risk Increase** | Running tasks in parallel increases the risk of rework if one task's output affects the other |
| **Resource Constraints** | Overlapping tasks may require more resources simultaneously |
| **Quality Impact** | Parallel execution may reduce review and quality control time |

## Crashing

Crashing means adding additional resources or funding to shorten the duration of critical-path activities.

### How It Works

1. The module identifies activities on the **critical path**
2. For each, it calculates:
   - **Normal Duration** — Planned duration without additional resources
   - **Crash Duration** — Shortest possible duration with maximum resources
   - **Normal Cost** — Cost at normal duration
   - **Crash Cost** — Cost at crash duration
   - **Cost Slope** — (Crash Cost - Normal Cost) / (Normal Duration - Crash Duration) — the cost per day saved

3. Activities are ranked by **cost slope** (lowest first) to identify the cheapest way to shorten the schedule

### Crashing Decision Table

| Metric | Description |
|---|---|
| **Cost Slope** | The incremental cost to save one day — lower is better |
| **Max Compression** | Maximum number of days that can be saved on this activity |
| **Impact on Float** | How crashing this activity affects float of downstream activities |

## Scenario Comparison

The module supports creating and comparing multiple schedule scenarios:

| Feature | Description |
|---|---|
| **Baseline vs. Compressed** | Compare the original schedule against the compressed alternative |
| **Multiple Scenarios** | Create several compression options with different trade-offs |
| **Cost-Duration Trade-off** | Visualize the relationship between added cost and schedule reduction |
| **What-If Analysis** | Test the impact of compressing specific activities |
