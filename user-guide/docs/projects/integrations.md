---
sidebar_position: 23
title: Project Integrations
description: Connect projects to external government systems (PFMS, GeM, CPPP, GSTN, PARIVESH)
---

# Project Integrations

The **Integrations** tab at the project level shows the status of external system connections configured for this project. These integrations connect Bipros EPPM to Indian government systems for financial management, procurement, tax compliance, and environmental clearance.

## Accessing Project Integrations

Navigate to a project and click the **Integrations** tab (available via query parameter `?tab=integrations`).

![Project Integrations](/img/screenshots/40-project-integrations.png)

## Available Integrations

| System | Full Name | Purpose |
|---|---|---|
| **PFMS** | Public Financial Management System | Track government fund releases and initiate payments |
| **GeM** | Government e-Marketplace | Place and track procurement orders through the government marketplace |
| **CPPP** | Central Public Procurement Portal | Publish tenders and track bid submissions |
| **GSTN** | Goods and Services Tax Network | Verify contractor GST registration and compliance status |
| **PARIVESH** | PARIVESH Portal | Track environmental and forest clearance status |

## Integration Details

### PFMS (Public Financial Management System)

| Feature | Description |
|---|---|
| **Fund Status Checking** | Query the current fund allocation and disbursement status |
| **Payment Initiation** | Initiate payments through PFMS from within Bipros EPPM |
| **Fund Transfer Tracking** | Monitor the status of fund transfers |
| **Sanction Order Mapping** | Link project activities to specific sanction orders |

### GeM (Government e-Marketplace)

| Feature | Description |
|---|---|
| **Order Placement** | Place procurement orders on GeM |
| **Order Tracking** | Monitor order status (PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED) |
| **Catalog Integration** | Browse available items from the GeM catalog |
| **Vendor Management** | Manage GeM-registered vendors |

### CPPP (Central Public Procurement Portal)

| Feature | Description |
|---|---|
| **Tender Publishing** | Publish new tenders on the CPPP portal |
| **Bid Tracking** | Monitor received bids and their evaluation status |
| **Tender Status** | Track the lifecycle of each tender |

### GSTN (Goods and Services Tax Network)

| Feature | Description |
|---|---|
| **Contractor Verification** | Verify a contractor's GST registration status |
| **GST Compliance Check** | Confirm that contractors are compliant with GST filing requirements |
| **Tax Status Validation** | Check return filing status and compliance rating |

### PARIVESH

| Feature | Description |
|---|---|
| **Clearance Tracking** | Track the status of environmental and forest clearance applications |
| **Regulatory Compliance** | Ensure project activities comply with environmental regulations |
| **Document Submission** | Monitor document submission status for clearance applications |

## Integration Status

Each integration shows its current connection status:

| Status | Meaning |
|---|---|
| **ACTIVE** | Integration is configured and working correctly |
| **INACTIVE** | Integration is configured but currently disabled |
| **ERROR** | Integration encountered an error — check configuration |
| **NOT_CONFIGURED** | Integration has not been set up yet |

:::info
Integration configuration is managed at the system level by Administrators. See [Admin > Integrations](../admin/integrations) for setup instructions.
:::

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| PFMS | Public Financial Management System |
| GeM | Government e-Marketplace |
| CPPP | Central Public Procurement Portal |
| GSTN | Goods and Services Tax Network |
| GST | Goods and Services Tax |
