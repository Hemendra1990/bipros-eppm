---
sidebar_position: 2
title: Calendars
description: Define working times, holidays, and non-working days
---

# Calendars

**Calendars** define the working-time rules used by the scheduling engine. They determine which days are working days, what hours are available for work, and which dates are holidays or non-working days.

## Accessing Calendars

Click **Calendars** in the sidebar, or navigate to **Admin > Calendars** to create new calendars.

![Calendars](/img/screenshots/18-admin-calendars.png)

## Why Calendars Matter

The CPM scheduling engine uses calendars to:

- **Calculate activity durations** — A 5-day activity on a Monday-Saturday calendar will finish sooner than on a Monday-Friday calendar
- **Skip non-working days** — Holidays and weekends are automatically excluded from duration calculations
- **Handle resource availability** — Each resource can have its own calendar reflecting its specific working pattern

## Calendar Properties

| Field | Description |
|---|---|
| **Calendar Name** | Descriptive name (e.g., "Standard 6-Day Week", "Monsoon Season Calendar") |
| **Calendar Type** | Type classification (GLOBAL, PROJECT, RESOURCE) |
| **Work Week** | Which days of the week are working days and their working hours |
| **Holidays** | Specific non-working dates (e.g., national holidays, site closures) |

## Work Week Configuration

For each day of the week, you can configure:

| Setting | Description |
|---|---|
| **Day Type** | WORKING or NON_WORKING |
| **Start Time** | When work begins on this day (e.g., `08:00`) |
| **End Time** | When work ends on this day (e.g., `17:00`) |
| **Hours Per Day** | Available working hours (e.g., `8.0`) |

### Example: Standard 6-Day Calendar

| Day | Type | Hours |
|---|---|---|
| Monday | WORKING | 08:00 - 17:00 (8 hrs) |
| Tuesday | WORKING | 08:00 - 17:00 (8 hrs) |
| Wednesday | WORKING | 08:00 - 17:00 (8 hrs) |
| Thursday | WORKING | 08:00 - 17:00 (8 hrs) |
| Friday | WORKING | 08:00 - 17:00 (8 hrs) |
| Saturday | WORKING | 08:00 - 13:00 (4 hrs) |
| Sunday | NON_WORKING | — |

## Creating a Calendar

1. Navigate to **Admin > Calendars** and click **New Calendar**
2. Enter the calendar name and type
3. Configure the work week (which days are working/non-working and their hours)
4. Add any holidays or exception dates
5. Click **Save**

## Calendar Types

| Type | Description |
|---|---|
| **GLOBAL** | System-wide default calendar. Used when no specific calendar is assigned. |
| **PROJECT** | Project-specific calendar overriding the global default for a particular project. |
| **RESOURCE** | Resource-specific calendar reflecting individual resource availability (e.g., a piece of equipment that only operates on specific days). |

## Calendar Priority

When the scheduling engine calculates dates, it uses calendars in this priority order:

1. **Resource Calendar** (if assigned to the resource)
2. **Project Calendar** (if defined for the project)
3. **Global Calendar** (system default)

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| CPM | Critical Path Method |
