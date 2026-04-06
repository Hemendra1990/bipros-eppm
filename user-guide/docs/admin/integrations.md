---
sidebar_position: 3
title: Integration Configuration
description: Configure connections to external government systems (Admin only)
---

# Integration Configuration

The **Integrations** admin page allows administrators to configure connections to external government systems. Once configured, these integrations become available at the project level for data exchange.

:::info
This page is only accessible to users with the **Admin** role.
:::

## Accessing Integration Configuration

Click **Integrations** in the sidebar (bottom section of admin items).

![Admin Integrations](/img/screenshots/17-admin-integrations.png)

## Available Integrations

| System | Full Name | Purpose |
|---|---|---|
| **PFMS** | Public Financial Management System | Government fund management and payment processing |
| **GeM** | Government e-Marketplace | Government procurement marketplace |
| **CPPP** | Central Public Procurement Portal | Tender publishing and bid management |
| **GSTN** | Goods and Services Tax Network | Contractor tax compliance verification |
| **PARIVESH** | PARIVESH Portal | Environmental and forest clearance tracking |

## Configuration Fields

Each integration requires:

| Field | Description |
|---|---|
| **Integration Name** | Display name for the integration |
| **Base URL** | The API endpoint URL for the external system |
| **Auth Type** | Authentication method used to connect |
| **API Key / Credentials** | Authentication credentials (stored securely) |
| **Status** | ACTIVE (enabled) or INACTIVE (disabled) |
| **Last Sync** | Timestamp of the most recent data synchronization |

## Authentication Types

| Auth Type | Description |
|---|---|
| **NONE** | No authentication required (public APIs) |
| **API_KEY** | API key-based authentication — key passed in request header |
| **OAUTH2** | OAuth 2.0 flow with client credentials or authorization code |
| **JWT** | JSON Web Token-based authentication |

## Configuring an Integration

1. Click on the integration card or row you want to configure
2. Fill in the configuration fields:
   - **Base URL** — The API endpoint (provided by the government system)
   - **Auth Type** — Select the appropriate authentication method
   - **Credentials** — Enter the API key, client ID/secret, or JWT secret
3. Click **Test Connection** to verify the configuration
4. Click **Save**
5. Toggle the status to **ACTIVE** to enable the integration

## Integration Status Indicators

| Status | Icon | Meaning |
|---|---|---|
| **ACTIVE** | Green | Integration is configured and working |
| **INACTIVE** | Grey | Integration is configured but disabled |
| **ERROR** | Red | Connection failed — check configuration |

## Monitoring

The admin page shows:

- **Last Sync Timestamp** — When data was last exchanged
- **Connection Health** — Current connection status
- **Error Logs** — Recent errors (if any)

:::caution
Integration credentials (API keys, client secrets) are sensitive. Only Administrators should have access to this page. Credentials are stored encrypted and are never displayed in plain text after saving.
:::

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| PFMS | Public Financial Management System |
| GeM | Government e-Marketplace |
| CPPP | Central Public Procurement Portal |
| GSTN | Goods and Services Tax Network |
| API | Application Programming Interface |
| OAuth | Open Authorization |
| JWT | JSON Web Token |
