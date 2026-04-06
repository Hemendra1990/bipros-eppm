---
sidebar_position: 9
title: Contracts
description: Managing contracts, vendors, and contractual milestones
---

# Contracts

The **Contracts** module allows you to create and track contracts associated with a project. Each project can have multiple contracts covering different scopes of work.

## Accessing Contracts

Navigate to a project and click the **Contracts** tab.

![Contracts](/img/screenshots/23-project-contracts.png)

## Contracts Table

The contracts list displays:

| Column | Description |
|---|---|
| **Contract Number** | Unique contract identifier |
| **Contract Name** | Descriptive name of the contract |
| **Contractor** | Name of the contractor or vendor |
| **Contract Type** | Type of contract (Fixed Price, Time & Material, etc.) |
| **Contract Value** | Total awarded contract value |
| **LOA Date** | Date of the Letter of Award |
| **Start Date** | Contractual start date |
| **Completion Date** | Contractual completion date |
| **Status** | Current status: `ACTIVE`, `COMPLETED`, or `CANCELLED` |

## Creating a Contract

1. Click **Add Contract** (or **New Contract**)
2. Fill in the contract details:

| Field | Required | Description |
|---|---|---|
| **Contract Number** | Yes | Unique identifier (e.g., `CNT-2026-001`) |
| **Contract Name** | Yes | Full name describing the scope |
| **Contractor Name** | Yes | Name of the awarded vendor |
| **Contract Type** | Yes | Select from Fixed Price, Time & Material, etc. |
| **Contract Value** | Yes | Total contract value in project currency |
| **LOA Date** | Yes | Date the Letter of Award was issued |
| **Start Date** | Yes | When the contracted work begins |
| **Completion Date** | Yes | When the contracted work is expected to finish |
| **LD Rate** | No | Liquidated Damages rate (per day of delay) |
| **DLP Months** | No | Defect Liability Period duration in months |
| **Status** | Yes | Initial status (usually `ACTIVE`) |

3. Click **Save**

## Contract Detail View

Click a contract number or name to open its detail view. From here you can:

- Edit contract details
- Track billing and payment history
- Monitor progress against the contracted scope
- View associated RA (Running Account) bills

## Key Contractual Terms

| Term | Description |
|---|---|
| **LOA** | **Letter of Award** — The formal document awarding the contract to the vendor |
| **LD** | **Liquidated Damages** — Pre-agreed damages payable by the contractor for each day of delay beyond the contractual completion date |
| **DLP** | **Defect Liability Period** — The period after practical completion during which the contractor must rectify any defects at their own cost |
| **Retention** | A percentage of each payment withheld as security, released after defects liability |

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| LOA | Letter of Award |
| LD | Liquidated Damages |
| DLP | Defect Liability Period |
| RA | Running Account |
| T&M | Time and Material |
