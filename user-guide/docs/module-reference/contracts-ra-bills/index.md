---
sidebar_position: 1
title: Contracts & RA Bills — Deep Dive
description: Technical reference for contract management and running account bill processing
---

# Contracts & RA Bills — Deep Dive

## Overview

The Contracts module manages contractor agreements, BOQ rates, and payment processing via Running Account (RA) Bills.

## Actors & Roles

| Actor | Role |
|---|---|
| **Contract Manager** | Creates contracts, processes RA bills |
| **Cost Engineer** | Verifies quantities and rates |
| **Project Manager** | Approves RA bills |
| **Finance** | Processes payments |

## Use Cases

### UC-CNT-01: Create Contract

| Attribute | Value |
|---|---|
| **ID** | UC-CNT-01 |
| **Name** | Create Contract |
| **Actor** | Contract Manager |
| **Precondition** | Project exists, contractor is registered |
| **Trigger** | User clicks "New Contract" |

**Main Flow:**
1. User selects contractor
2. User enters contract value, start date, end date
3. User uploads contract document
4. User defines BOQ items with rates
5. System validates total contract value
6. System creates contract record

**Postcondition:** Contract is created with BOQ |

### UC-CNT-02: Process RA Bill

| Attribute | Value |
|---|---|
| **ID** | UC-CNT-02 |
| **Name** | Process RA Bill |
| **Actor** | Contract Manager |
| **Precondition** | Contract exists, DPRs are submitted |
| **Trigger** | User clicks "New RA Bill" |

**Main Flow:**
1. User selects contract and billing period
2. System auto-populates quantities from DPR
3. User reviews and adjusts quantities
4. System calculates amounts (Qty × Rate)
5. User adds deductions (TDS, Retention, LD)
6. System calculates net payable
7. User submits for approval

**Postcondition:** RA bill is submitted for approval |

## Deduction Formulas

$$\text{TDS} = \text{Gross} \times \text{TDS Rate}$$

$$\text{Retention} = \text{Gross} \times \text{Retention \%}$$

$$\text{Net Payable} = \text{Gross} - \text{TDS} - \text{Retention} - \text{LD} - \text{Advance Recovery}$$

## Related Modules

- [Cost Management](../cost-management/)
- [RA Bill Task Guide](../../task-guides/managing-ra-bills)
