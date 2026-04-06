---
sidebar_position: 1
title: Global Settings
description: System-wide configuration parameters (Admin only)
---

# Global Settings

The **Global Settings** page allows Administrators to configure system-wide parameters that affect the behaviour of the entire application.

:::info
This page is only accessible to users with the **Admin** role.
:::

## Accessing Settings

Click **Settings** in the sidebar (gear icon, near the bottom).

![Admin Settings](/img/screenshots/15-admin-settings.png)

## Settings Categories

Settings are grouped by functional area:

### General Settings

| Setting | Description | Example Value |
|---|---|---|
| **System Name** | Display name of the application | Bipros EPPM |
| **Default Currency** | Currency used for all financial calculations | INR |
| **Date Format** | How dates are displayed throughout the application | DD/MM/YYYY |
| **Financial Year Start** | First month of the fiscal year | April |

### Schedule Settings

| Setting | Description | Example Value |
|---|---|---|
| **Default Calendar** | The calendar used when no specific calendar is assigned | Standard 6-Day Week |
| **Hours Per Day** | Default working hours per day for duration calculations | 8 |
| **Scheduling Method** | Default scheduling calculation method | Retained Logic |

### Cost Settings

| Setting | Description | Example Value |
|---|---|---|
| **Base Currency** | Primary currency for cost tracking | INR |
| **Decimal Precision** | Number of decimal places for financial values | 2 |
| **Tax Rate** | Default GST/tax rate | 18% |

### Notification Settings

| Setting | Description | Example Value |
|---|---|---|
| **Email Notifications** | Enable or disable email alerts | Enabled |
| **Overdue Alert Threshold** | Days before an activity is flagged as overdue | 3 |
| **RFI Response Deadline** | Default response deadline for new RFIs | 7 days |

## Updating Settings

1. Navigate to the setting you want to change
2. Modify the value
3. Click **Save** (or **Update**)

:::caution
Changes to global settings affect all users and all projects immediately. Coordinate with your team before making changes to scheduling or cost settings, as they may impact ongoing calculations.
:::
