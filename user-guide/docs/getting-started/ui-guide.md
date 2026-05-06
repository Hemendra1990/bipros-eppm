---
sidebar_position: 5
title: User Interface Guide
description: Navigating the Bipros EPPM interface, common patterns, and UI conventions
---

# User Interface Guide

This guide explains the common UI patterns and conventions used throughout Bipros EPPM.

## Layout Overview

The Bipros EPPM interface consists of four main areas:

1. **Top Navigation Bar** — Logo, global search, notifications, user menu
2. **Left Sidebar** — Main navigation menu organised by module
3. **Main Content Area** — The primary workspace for the current page
4. **Contextual Panels** — Drawers, modals, and side panels for detailed actions

## Common UI Patterns

### Tables & Data Grids

Most lists in Bipros EPPM are presented as sortable, filterable tables:

| Feature | How to Use |
|---|---|
| **Sort** | Click any column header to sort ascending/descending |
| **Filter** | Use the search bar above the table for text filtering |
| **Pagination** | Navigate using the pagination controls at the bottom |
| **Row Actions** | Click the actions menu (⋮) on any row for edit, delete, view |
| **Bulk Actions** | Select multiple rows via checkboxes for batch operations |

### Forms

Forms in Bipros EPPM follow consistent patterns:

- **Required fields** are marked with a red asterisk (*)
- **Validation errors** appear below the field in red text
- **Auto-save** is not enabled — always click **Save** to persist changes
- **Cancel** discards all changes since the last save
- **Date pickers** use a calendar popup with quick-select options

### Tabs

Detail views (e.g., Project Detail) use tabs to organise large amounts of information:

- Click a tab to switch context without leaving the page
- Tabs with **unsaved changes** show a dot indicator
- Some tabs are **role-restricted** and hidden if the user lacks permission

### Drawers & Modals

- **Drawers** slide in from the right for creating/editing records without leaving the current page
- **Modals** appear centered for confirmations, alerts, and quick actions
- Press **Escape** or click the backdrop to close drawers and modals

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl + K` | Open global search |
| `Escape` | Close drawers, modals, dropdowns |
| `Ctrl + S` | Save current form (where supported) |

## Status Badges & Colours

Bipros EPPM uses colour-coded badges to indicate status:

| Colour | Meaning | Examples |
|---|---|---|
| **Green** | Success / On Track / Approved | `ACTIVE`, `APPROVED`, `COMPLETED` |
| **Yellow** | Warning / At Risk / Pending | `PLANNED`, `PENDING`, `AT RISK` |
| **Red** | Error / Overdue / Rejected | `OVERDUE`, `REJECTED`, `CRITICAL` |
| **Blue** | Information / In Progress | `IN PROGRESS`, `UNDER REVIEW` |
| **Gray** | Neutral / Draft / Inactive | `DRAFT`, `INACTIVE` |

## Notifications

The notification bell in the top-right corner displays:

- **System alerts** — Maintenance windows, updates
- **Workflow notifications** — Approvals required, task assignments
- **Due date reminders** — Upcoming deadlines, overdue items

Click the bell icon to view recent notifications. Unread notifications show a badge count.

## Mobile Responsiveness

Bipros EPPM is optimised for desktop use but supports tablet viewing:

- The sidebar collapses to a hamburger menu on smaller screens
- Tables support horizontal scrolling
- Forms stack vertically on narrow viewports
- For full functionality, a screen width of **1280px or greater** is recommended
