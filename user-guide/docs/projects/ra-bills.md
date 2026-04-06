---
sidebar_position: 10
title: RA Bills
description: Running Account bills — creating, tracking, and managing periodic billing
---

# RA Bills (Running Account Bills)

**Running Account (RA) Bills** are periodic payment claims submitted by contractors for work completed during a billing period. The RA Bills module tracks the entire billing lifecycle from submission through approval and payment.

## Accessing RA Bills

Navigate to a project, then select **RA Bills** from the **More** dropdown menu or the Contracts-related tabs.

![RA Bills](/img/screenshots/24-project-ra-bills.png)

## RA Bills Table

The bills list displays:

| Column | Description |
|---|---|
| **Bill Number** | Sequential bill number (e.g., `RA-001`, `RA-002`) |
| **Period From** | Start date of the billing period |
| **Period To** | End date of the billing period |
| **Gross Amount** | Total value of work claimed before deductions |
| **Deductions** | Total deductions (retention, advances, LD, taxes) |
| **Net Amount** | Amount payable after deductions (Gross - Deductions) |
| **Contract** | The contract this bill is associated with |
| **Status** | Current bill status |
| **Remarks** | Notes or observations |

## Bill Statuses

| Status | Meaning |
|---|---|
| **PENDING** | Bill has been submitted and is awaiting review |
| **APPROVED** | Bill has been reviewed and approved for payment |
| **PAID** | Payment has been disbursed to the contractor |
| **REJECTED** | Bill has been rejected and returned for correction |

## Creating an RA Bill

1. Click **Add RA Bill** (or **New Bill**)
2. Fill in the bill details:

| Field | Required | Description |
|---|---|---|
| **Bill Number** | Yes | Unique sequential number |
| **Contract** | Yes | Select the contract this bill relates to |
| **Period From** | Yes | Start of the billing period |
| **Period To** | Yes | End of the billing period |
| **Gross Amount** | Yes | Total value of work completed in this period |
| **Deductions** | No | Any amounts to be deducted (retention, LD, etc.) |
| **Net Amount** | Auto | Calculated as Gross Amount minus Deductions |
| **Remarks** | No | Additional notes or justifications |

3. Click **Save**

## Bill Lifecycle

```
PENDING → APPROVED → PAID
    ↘ REJECTED (returned for correction)
        ↘ PENDING (re-submitted after correction)
```

## Common Deductions

| Deduction Type | Description |
|---|---|
| **Retention** | Percentage withheld as security (typically 5-10%) |
| **Advance Recovery** | Recovery of mobilization or material advances |
| **Liquidated Damages** | Deduction for schedule delays beyond contractual dates |
| **GST / TDS** | Tax deductions as per applicable regulations |
| **Other Recoveries** | Material supplied, equipment hired, etc. |

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| RA | Running Account |
| LD | Liquidated Damages |
| GST | Goods and Services Tax |
| TDS | Tax Deducted at Source |
