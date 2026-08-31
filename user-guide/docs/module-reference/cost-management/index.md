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

## BOQ Commercial Chain

Bipros EPPM models a single, end-to-end commercial chain that ties the contract Bill of Quantities to schedule execution, change control, and contractor billing. Once the BOQ is loaded, every quantity captured in the field flows through this chain automatically.

### Chain Overview

```
Contract BOQ → BOQ Items → WBS / Activity links → DPR (executed qty)
                                                   ↓
                Variation Order (VO) → BOQ adjustments
                                                   ↓
                                   RA Bill Draft → RA Bill (payment)
```

### Key Entities

| Entity | What It Holds |
|---|---|
| **BOQ Item** | Item No, description, unit, `boqQty`, `boqRate`, `boqAmount` (qty × rate), `budgetedRate`, `qtyExecutedToDate`, `actualRate`, `percentComplete`, `costVariance`, `chapter`, `status` |
| **BOQ Status** | `PENDING`, `ACTIVE`, `COMPLETED`, `OVERRUN`, `ON_HOLD` — auto-derived from execution, except `ON_HOLD` (manual) |
| **WBS Link** | Each BOQ item may be linked to a WBS node so that BOQ totals roll up the WBS hierarchy |
| **Material BOQ Link** | Connects materials consumed at the site back to the BOQ item being executed |
| **Variation Order (VO) Line Item** | Adds, removes, or revises BOQ quantities/rates on contract amendment |
| **RA Bill Item** | Carries a `boqItemId` foreign key so every billed line is traceable to its BOQ origin |

### How Pricing Flows Through the Chain

1. **Contract BOQ is loaded** at project setup — each line carries the contract `boqRate` (the price the client will pay) and `boqQty` (the awarded quantity).
2. **Project team adds a `budgetedRate`** — the planned internal cost per unit. The difference between `boqRate` and `budgetedRate` is the planned commercial margin per unit.
3. **DPR submission updates `qtyExecutedToDate`** — when a Daily Progress Report is approved, the `DprBoqSyncListener` subtracts the executed quantity from the BOQ item's remaining-to-execute balance and recomputes `percentComplete`.
4. **`actualRate` is captured from site cost reports** — combined with `qtyExecutedToDate` it produces `actualAmount` and `costVariance` (positive variance = over-budget execution).
5. **VO approval mutates BOQ in-place** — when a VO is approved, `VoApprovedBoqMutationListener` applies each line item (ADD / REVISE / DELETE) before commit and writes an audit log entry.
6. **RA Bill Draft is generated** — the `RaBillDraftCalculator` walks the BOQ items, picks up unbilled executed quantities at contract `boqRate`, and proposes line items the cost engineer can preview, edit, and save as an RA bill.
7. **Overrun protection** — when `qtyExecutedToDate > boqQty` the item flips to `OVERRUN`. The unbilled overrun value is shown as a banner on the BOQ page with a direct **Create VO** link; the quantity cannot be billed via RA bill until a VO restores headroom.

### Where BOQ Affects EVM and Budget

- **BAC (Budget at Completion)** for activities tied to a BOQ item rolls up `boqQty × budgetedRate`.
- **Earned Value (EV)** at the activity level uses `qtyExecutedToDate × budgetedRate` (budgeted cost of work performed).
- **Actual Cost (AC)** uses `qtyExecutedToDate × actualRate`.
- **Cost Variance** at the BOQ line is `actualAmount − (qtyExecutedToDate × budgetedRate)`; rolled up, this feeds the project CV.

### BOQ-Specific Use Cases

### UC-CST-04: Load Contract BOQ

| Attribute | Value |
|---|---|
| **ID** | UC-CST-04 |
| **Name** | Load Contract BOQ |
| **Actor** | Cost Engineer |
| **Precondition** | Project is created; contract is awarded with a priced BOQ |
| **Trigger** | User opens the project's **BOQ** tab and clicks **Add Item** (or imports in bulk) |

**Main Flow:**
1. User enters Item No, description, unit, `boqQty`, `boqRate`
2. User optionally links the item to a WBS node and assigns a chapter (e.g. "3 - Bituminous")
3. User sets the internal `budgetedRate`
4. System computes `boqAmount = boqQty × boqRate` and `budgetedAmount = boqQty × budgetedRate`
5. System sets initial status to `PENDING`

**Postcondition:** BOQ items exist on the project and are visible to DPR, VO, and RA-bill workflows.

### UC-CST-05: Capture Site Execution into BOQ

| Attribute | Value |
|---|---|
| **ID** | UC-CST-05 |
| **Name** | Capture Site Execution into BOQ |
| **Actor** | Site Engineer (DPR), Cost Engineer (rate) |
| **Precondition** | BOQ items exist; an approved DPR records executed quantities against BOQ items |
| **Trigger** | DPR approval event |

**Main Flow:**
1. DPR approval fires `DprSubmittedEvent`
2. `DprBoqSyncListener` increments `qtyExecutedToDate` on each affected BOQ item
3. Cost Engineer enters the period's `actualRate` from the site cost report (inline edit on the BOQ row)
4. System recomputes `percentComplete`, `actualAmount`, `costVariance`, and updates `status` automatically
5. If `qtyExecutedToDate > boqQty`, item flips to `OVERRUN` and the project banner surfaces the unbilled overrun total

**Postcondition:** BOQ rows reflect actual site execution; OVERRUN items are flagged for VO action.

### UC-CST-06: Apply Variation Order to BOQ

| Attribute | Value |
|---|---|
| **ID** | UC-CST-06 |
| **Name** | Apply Variation Order to BOQ |
| **Actor** | Contract Manager |
| **Precondition** | An approved Variation Order with line items exists |
| **Trigger** | VO status transitions to APPROVED |

**Main Flow:**
1. `VariationOrderApprovedEvent` is published with `impactedBoqItemIds` and line-item payloads
2. `VoApprovedBoqMutationListener` applies each `VoLineItemAction` (`ADD_NEW_ITEM`, `REVISE_QTY`, `REVISE_RATE`, `DELETE_ITEM`) before transaction commit
3. System writes an audit-log entry per change
4. OVERRUN items whose quantities are now within revised `boqQty` are returned to ACTIVE/COMPLETED automatically

**Postcondition:** BOQ matches the post-VO contract state; downstream RA-bill drafts recognise the new headroom.

### UC-CST-07: Generate RA Bill Draft from BOQ

| Attribute | Value |
|---|---|
| **ID** | UC-CST-07 |
| **Name** | Generate RA Bill Draft from BOQ |
| **Actor** | Cost Engineer |
| **Precondition** | BOQ items have unbilled executed quantities |
| **Trigger** | User clicks **Generate Draft** on the RA Bills page |

**Main Flow:**
1. `RaBillDraftService` calls the stateless `RaBillDraftCalculator`
2. Calculator walks each BOQ item, computes the unbilled quantity since the last RA bill, and proposes a line at the contract `boqRate`
3. User previews the draft, edits quantities/rates if required, and clicks **Save**
4. System creates the RA bill with each `RaBillItem.boqItemId` linked back to its source

**Postcondition:** A draft RA bill exists, traceable line-by-line to BOQ items.

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
| **RA Bill Item** | Line item within an RA bill — links back to its source `BoqItem` via `boqItemId` |
| **BOQ Item** | Contract Bill of Quantities line — anchors the commercial chain (see [BOQ Commercial Chain](#boq-commercial-chain)) |
| **VO Line Item** | Adds, revises, or deletes BOQ quantities/rates when a Variation Order is approved |

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
