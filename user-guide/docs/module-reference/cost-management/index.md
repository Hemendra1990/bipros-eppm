---
sidebar_position: 1
title: Cost Management — Deep Dive
description: Technical reference for budgets, cost accounts, funding, and cash flow
---

# Cost Management — Deep Dive

## Overview

The Cost Management module handles project budgeting, cost tracking, funding sources, cash flow forecasting, and period-based cost aggregation.

## Actors & Roles

| Actor | Role in This Module |
|---|---|
| **Cost Engineer** | Primary user — sets up budgets, tracks actuals, manages cost accounts |
| **Project Manager** | Approves budget changes, reviews cost reports |
| **Contract Manager** | Processes RA bills, manages contract values |
| **Finance Team** | Manages funding sources, cash flow, payment processing |

## Use Cases

### UC-CST-01: Set Up Project Budget

| Attribute | Value |
|---|---|
| **ID** | UC-CST-01 |
| **Name** | Set Up Project Budget |
| **Actor** | Cost Engineer |
| **Precondition** | Project exists, WBS is defined |
| **Trigger** | User clicks "Set Budget" |

**Main Flow:**
1. User selects WBS node to budget
2. User enters budget amount
3. User selects cost account
4. System validates budget against funding source limits
5. System saves budget allocation
6. System updates BAC (Budget at Completion)

**Postcondition:** Budget is allocated to WBS node |

### UC-CST-02: Process Budget Change

| Attribute | Value |
|---|---|
| **ID** | UC-CST-02 |
| **Name** | Process Budget Change |
| **Actor** | Cost Engineer |
| **Precondition** | Budget exists, change reason is documented |
| **Trigger** | User requests budget increase/decrease |

**Main Flow:**
1. User selects WBS node
2. User enters new budget amount
3. User selects change reason (scope change, escalation, contingency)
4. User attaches supporting documentation
5. System logs the change in Budget Change Log
6. System updates BAC

**Postcondition:** Budget is changed and change is auditable |

### UC-CST-03: Create Cash Flow Forecast

| Attribute | Value |
|---|---|
| **ID** | UC-CST-03 |
| **Name** | Create Cash Flow Forecast |
| **Actor** | Finance Team |
| **Precondition** | Budget and schedule are established |
| **Trigger** | User clicks "Cash Flow Forecast" |

**Main Flow:**
1. User selects forecast period (monthly/quarterly)
2. System distributes budget across periods based on schedule
3. User adjusts for known payment terms (e.g., 30-day delay)
4. System generates cash flow S-curve
5. User exports forecast report

**Postcondition:** Cash flow forecast is stored |

## Core Formulas

### Cost Account Hierarchy Rollup

$$\text{Cost Account Total} = \sum_{i=1}^{n} \text{WBS Budget}_i$$

Where WBS Budgets are allocated to that cost account.

### Budget Change Log

$$\text{Current Budget} = \text{Original Budget} + \sum \text{Increases} - \sum \text{Decreases}$$

### Period Cost Aggregation

$$\text{Period Cost}_t = \sum_{i=1}^{n} (\text{Activity Budget}_i \times \text{Period Weight}_i)$$

Where Period Weight is the proportion of the activity's budget expected in period $t$.

### RA Bill Amount

$$\text{RA Bill Amount} = \sum_{j=1}^{m} (\text{Quantity}_j \times \text{Rate}_j)$$

Where Quantity is from DPR and Rate is from contract BOQ.

### Net Payable

$$\text{Net Payable} = \text{Gross Amount} - \text{TDS} - \text{Retention} - \text{LD} - \text{Advance Recovery}$$

## Data Model

### Key Entities

| Entity | Description |
|---|---|
| **Cost Account** | Hierarchical cost coding structure (e.g., CSI codes) |
| **Project Budget** | Budget allocation per WBS node |
| **Budget Change Log** | Audit trail of all budget modifications |
| **Funding Source** | Source of funds (government, loan, equity) |
| **Financial Period** | Defined reporting periods (monthly, quarterly) |
| **Cash Flow Forecast** | Projected expenditure over time |
| **RA Bill** | Running account bill for contractor payments |
| **RA Bill Item** | Line item within an RA bill |

## Business Rules

1. **Budget cannot exceed funding** — Total budget across all WBS nodes cannot exceed the total funding source allocation.
2. **Budget changes require approval** — Changes beyond a threshold require Project Manager approval.
3. **RA bill quantities trace to DPR** — All RA bill quantities must be traceable to approved DPR entries.
4. **Retention is auto-calculated** — Typically 5–10% of gross amount, released after DLP.
5. **TDS is auto-calculated** — Based on Indian tax regulations and contractor type.

## Related Modules

- [EVM](../evm/)
- [Contracts & RA Bills](../contracts-ra-bills/)
- [RA Bills Task Guide](../../task-guides/managing-ra-bills)
