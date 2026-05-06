---
sidebar_position: 1
title: Integrations — Deep Dive
description: Technical reference for PFMS, GeM, GSTN, CPPP, and PARIVESH integrations
---

# Integrations — Deep Dive

## Overview

Bipros EPPM integrates with multiple Indian government systems to streamline procurement, payments, tax verification, and environmental clearances.

## Supported Integrations

| System | Full Name | Purpose |
|---|---|---|
| **PFMS** | Public Financial Management System | Government payment processing, fund transfer |
| **GeM** | Government e-Marketplace | Online procurement of goods and services |
| **GSTN** | Goods and Services Tax Network | Taxpayer verification, GST compliance |
| **CPPP** | Central Public Procurement Portal | Tender publishing and bid management |
| **PARIVESH** | PARIVESH Portal | Environmental clearance tracking |

## PFMS Integration

### Purpose
Process contractor payments through the government's PFMS platform.

### Data Flow
```
Bipros EPPM → RA Bill Approved → Payment Request → PFMS API → Fund Transfer → Status Update → Bipros EPPM
```

### Configuration (Admin)
1. Navigate to **Admin > Integrations > PFMS**
2. Enter API credentials
3. Configure scheme codes
4. Map project funding sources to PFMS schemes

## GeM Integration

### Purpose
Procure materials and services through the Government e-Marketplace.

### Data Flow
```
Bipros EPPM → Material Requirement → GeM API → Search Products → Place Order → Order Status → Bipros EPPM
```

## GSTN Integration

### Purpose
Verify contractor GST registration and compliance.

### API Endpoint
- GSTIN verification
- Returns filing status check

## CPPP Integration

### Purpose
Publish tenders and manage bids on the Central Public Procurement Portal.

### Features
- Tender creation and publishing
- Bid opening and evaluation
- Award notification

## PARIVESH Integration

### Purpose
Track environmental clearance applications.

### Features
- Application status check
- Document submission
- Clearance certificate retrieval

## Configuration

System Administrators configure integrations via **Admin > Integrations**:

| Setting | Description |
|---|---|
| **API Base URL** | Endpoint for the external system |
| **API Key / Token** | Authentication credentials |
| **Timeout** | Request timeout in seconds |
| **Retry Policy** | Number of retries on failure |
| **Webhook URL** | Callback URL for async notifications |

## Error Handling

| Error | Cause | Resolution |
|---|---|---|
| **Connection Timeout** | External system unavailable | Retry or check network |
| **Authentication Failed** | Invalid credentials | Update API key |
| **Data Validation Error** | Missing required fields | Check data mapping |
| **Rate Limited** | Too many requests | Implement backoff |

## Related Modules

- [Admin Integrations](../../admin/integrations)
- [Projects Integrations](../../projects/integrations)
