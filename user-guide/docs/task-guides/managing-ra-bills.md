---
sidebar_position: 6
title: Managing Running Account Bills
description: How to create, verify, and process RA Bills in Bipros EPPM
---

# Managing Running Account Bills

A **Running Account (RA) Bill** is a periodic payment mechanism where contractors submit bills for work completed during each billing period. This guide covers the complete RA bill lifecycle.

## Prerequisites

- Contract exists for the project
- BOQ items are defined with rates
- DPRs have been submitted for the billing period
- You have `RA_BILL_PROCESS` permission (Contract Manager or Cost Engineer)

## Understanding the RA Bill Lifecycle

```
Draft → Submitted → Verified → Approved → Paid
```

## Step 1: Create a New RA Bill

1. Navigate to your project
2. Click the **Contracts > RA Bills** tab
3. Click **New RA Bill**
4. Select:
   - **Contract** — The contract this bill applies to
   - **Billing Period** — Start and end dates
   - **Bill Type** — Interim or Final

## Step 2: Auto-Populate from DPR

1. Click **Auto-Populate from DPR**
2. The system retrieves quantities from DPRs within the billing period
3. For each BOQ item, the system calculates:

$$\text{Current Bill Quantity} = \text{Cumulative DPR Quantity} - \text{Previous Bill Quantity}$$

$$\text{Current Bill Amount} = \text{Current Bill Quantity} \times \text{BOQ Rate}$$

## Step 3: Manual Adjustments

Review each line item and adjust if needed:

| Field | Description |
|---|---|
| **BOQ Item** | Description from the Bill of Quantities |
| **Rate** | Unit rate as per contract (read-only) |
| **Current Quantity** | Quantity for this billing period |
| **Current Amount** | $Current Quantity \times Rate$ |
| **Cumulative Quantity** | Running total across all bills |
| **Cumulative Amount** | Running total amount |

## Step 4: Add Deductions

1. Expand the **Deductions** section
2. Add applicable deductions:
   - **TDS (Tax Deducted at Source)** — As per Indian tax regulations
   - **Liquidated Damages (LD)** — For delays, if applicable
   - **Retention Money** — Typically 5–10% of bill amount
   - **Advance Recovery** — Recovery of any advance payments

### Deduction Formulas

$$\text{TDS} = \text{Gross Amount} \times \text{TDS Rate}$$

$$\text{Retention} = \text{Gross Amount} \times \text{Retention \%}$$

$$\text{Net Payable} = \text{Gross Amount} - \sum(\text{Deductions})$$

## Step 5: Submit for Verification

1. Review the summary:
   - Gross Amount
   - Total Deductions
   - Net Payable
2. Attach supporting documents (scanned copies, measurement sheets)
3. Click **Submit for Verification**

## Step 6: Verification & Approval Workflow

| Stage | Actor | Action |
|---|---|---|
| **Verification** | Cost Engineer | Validates quantities and rates |
| **Approval** | Project Manager / Contract Manager | Authorises payment |
| **Finance** | Finance Team | Processes payment via PFMS (if integrated) |

## Expected Outcome

- RA Bill is created with a unique bill number
- Quantities are traceable back to DPR entries
- Deductions are calculated automatically
- Approval workflow is triggered

## Related Documentation

- [Contracts](../projects/contracts)
- [RA Bills](../projects/ra-bills)
- [Cost Management](../module-reference/cost-management/)
