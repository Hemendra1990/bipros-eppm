---
sidebar_position: 9
title: Running Schedule Compression
description: How to crash or fast-track your schedule to meet deadlines
---

# Running Schedule Compression

When your project is behind schedule or needs to finish earlier, **Schedule Compression** techniques can help. This guide covers crashing (adding resources) and fast-tracking (overlapping activities).

## Prerequisites

- CPM schedule is calculated and baselined
- You have `SCHEDULE_UPDATE` permission
- Activity cost data is available for crashing analysis

## Understanding Compression Techniques

| Technique | Description | Impact |
|---|---|---|
| **Crashing** | Add resources to critical activities to reduce duration | Increases cost |
| **Fast-Tracking** | Perform activities in parallel that were originally sequential | Increases risk |

## Step 1: Identify Compression Opportunities

1. Navigate to your project
2. Click **Schedule > Compression Analysis**
3. The system analyses all critical path activities and displays:

| Column | Description |
|---|---|
| **Activity** | Critical path activity name |
| **Current Duration** | Original duration in days |
| **Crash Duration** | Minimum possible duration |
| **Cost Slope** | Cost per day saved (see formula below) |
| **Compressible Days** | $Current - Crash$ |

### Crash Cost Slope

$$\text{Cost Slope} = \frac{\text{Crash Cost} - \text{Normal Cost}}{\text{Normal Duration} - \text{Crash Duration}}$$

Where:
- **Normal Cost** = Cost at original duration
- **Crash Cost** = Cost at crashed duration
- **Normal Duration** = Original duration
- **Crash Duration** = Minimum achievable duration

**Example:**
- Normal: 10 days, ₹1,00,000
- Crash: 7 days, ₹1,30,000
- Cost Slope = $(1,30,000 - 1,00,000) / (10 - 7) = ₹10,000$ per day

## Step 2: Select Activities to Crash

1. Sort the compression table by **Cost Slope** (ascending)
2. Select activities with the **lowest cost slope** first
3. Enter the number of days to compress for each selected activity

## Step 3: Run Compression

1. Click **Apply Compression**
2. The system recalculates:
   - New activity durations
   - Updated critical path
   - Total compression cost
   - New project finish date

### Compression Verification

$$\text{Total Compression Cost} = \sum_{i=1}^{n} (\text{Days Compressed}_i \times \text{Cost Slope}_i)$$

## Step 4: Review Impact

Compare before and after:

| Metric | Before | After |
|---|---|---|
| Project Duration | X days | Y days |
| Project Cost | ₹A | ₹B |
| Critical Path | Path 1 | Path 2 (may shift) |

## Step 5: Save as Scenario (Optional)

1. Click **Save as Scenario**
2. Name the scenario (e.g., "Crash by 2 Weeks")
3. Compare scenarios using **Schedule > Scenario Comparison**

## Fast-Tracking

To fast-track:

1. Identify activities on the critical path with **Finish-to-Start** dependencies
2. Change the relationship to **Start-to-Start** or **Overlap** with a lead
3. Re-run CPM to verify the schedule
4. Assess increased risk and document in the risk register

## Expected Outcome

- Compressed schedule with reduced project duration
- Documented compression cost
- Risk assessment for fast-tracked activities
- Saved scenario for comparison

## Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| No compressible activities | All activities at crash duration | Review scope or accept delay |
| Critical path shifts | New path becomes critical after compression | Continue compressing new critical path |
| Cost too high | Selected high-cost-slope activities | Prioritise low-cost-slope activities |

## Related Documentation

- [Schedule Compression](../projects/schedule-compression)
- [CPM Scheduling](./scheduling-activities)
- [Risk Analysis](./conducting-risk-analysis)
