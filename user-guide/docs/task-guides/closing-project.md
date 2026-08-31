---
sidebar_position: 11
title: Closing a Project
description: How to properly close out a completed project in Bipros EPPM
---

# Closing a Project

Project closure ensures all work is complete, documentation is archived, and lessons learned are captured. This guide covers the formal closure process.

## Prerequisites

- All activities are marked 100% complete
- All RA bills are processed and paid
- All permits are closed or transferred
- All risks are closed or transferred
- You have `PROJECT_UPDATE` permission

## Step 1: Verify Completion

1. Navigate to the project
2. Review the **Activities** tab — ensure all activities show 100% completion
3. Review the **EVM** tab — verify:
   - $EV = BAC$ (Earned Value equals Budget at Completion)
   - $CPI \approx 1.0$ (cost performance is neutral)

## Step 2: Close Outstanding Items

### Contracts & RA Bills
- Verify all final RA bills are submitted and paid
- Close the contract record

### Documents
- Ensure all as-built drawings are uploaded
- Archive RFIs and correspondence

### Permits
- Verify no permits are nearing expiry
- Close or transfer permits as needed

### Risks
- Close all resolved risks
- Transfer open risks to the operations team

## Step 3: Capture Lessons Learned

1. Go to **Project > Lessons Learned**
2. Document:
   - What went well
   - What could be improved
   - Recommendations for future projects

## Step 4: Update Project Status

1. Open the project detail page
2. Change **Status** from `ACTIVE` to `COMPLETED`
3. Set the **Actual Finish Date**
4. Add closure remarks

## Step 5: Archive the Project

1. The system automatically:
   - Locks the schedule from further changes
   - Preserves all historical data
   - Removes the project from active dashboards
   - Retains the project in portfolio reports

## Step 6: Generate Closure Report

1. Go to **Reports > Project Closure Report**
2. The report includes:
   - Project summary (scope, schedule, cost)
   - EVM final metrics
   - Resource utilisation summary
   - Risk summary
   - Document inventory

## Expected Outcome

- Project status is `COMPLETED`
- All historical data is preserved
- Project appears in completed project lists
- Closure report is generated and archived

## Related Documentation

- [Projects Overview](../projects/overview)
- [EVM](../projects/evm)
- [Reports & Analytics](../reports-analytics/reports)
