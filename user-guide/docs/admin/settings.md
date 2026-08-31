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

## Theme & Palette

The **Themes** tab (`Settings → Themes`) controls the visual appearance of the entire application — colour palette, border radius, font family, app name, and logo. Themes are managed centrally by Admins and made available to all users.

### Predefined themes

The application ships with a curated gallery of predefined palettes (Classic Gold, Ocean Blue, Emerald Forest, Sunrise, Arctic, Slate Corporate, Coffee Roast, Mint Fresh, Navy Command, Bordeaux, Sandstone, Steelworks, Olive Branch, Graphite, and others). Each predefined theme defines a coordinated **light** and **dark** palette so the standard light/dark toggle in the user menu continues to work seamlessly.

Click any tile in the **Predefined Themes** gallery to switch the active theme. Selection is applied instantly across the application.

### Custom theme builder

Administrators can create custom palettes via **Create Custom Theme** in the Themes tab. The builder opens with a side-by-side editor and a live **Theme Preview** so changes can be evaluated against a representative slice of the app before saving.

| Section | Description |
|---|---|
| **Light palette / Dark palette** | Twin colour pickers for background, foreground, accent, borders, and semantic states (success, warning, danger, info). Both palettes are required so the theme works correctly with the user-level light/dark toggle. |
| **Border radius** | One of `4 / 6 / 8 / 12` px, applied globally to cards, buttons, inputs, and dialogs. |
| **Font family** | Default (Inter), Fraunces (display), Inter (sans), or JetBrains Mono. |
| **App name** | Primary and secondary words shown in the sidebar header and on the sign-in page (see [Branding](#branding-app-name--logo) below). |
| **Logo images** | Separate light- and dark-mode uploads. If only one is provided, it is used as a fallback for the other mode. |

A maximum of **20 custom themes** can be saved per organisation.

### Editing and deleting custom themes

Saved custom themes appear under **Saved Themes**, beneath the predefined gallery. Each card has **Edit** and **Delete** controls (Admin only). Editing a theme reopens the builder pre-populated with the existing values; cancelling restores the previously active theme exactly as it was before the builder opened.

### Persistence behaviour

| Layer | Stored where | Scope |
|---|---|---|
| **Active theme ID** | Backend (`THEME` category in the settings table) + cached in browser local storage | Per user — every user can pick which available theme they want to see |
| **Custom theme catalogue** | Backend (settings table) | Organisation-wide — created and edited by Admins, visible to all users |
| **Theme CSS cache** | Browser local storage | Per device — used to avoid a flash of unstyled content on subsequent loads |

Because the active theme is persisted server-side, signing in from a new browser or device restores the user's last-chosen theme automatically. Local-storage caches are refreshed in the background whenever the active theme changes.

### Branding (App Name & Logo)

Branding is configured **as part of a theme**, not as a separate page. To change the application's display name or logo:

1. Open `Settings → Themes`
2. Either click **Create Custom Theme** to start from a copy of the active palette, or **Edit** an existing custom theme
3. In the **App Name** section, set:
   - **Primary name** — the bold first word (default: *Bipros*)
   - **Secondary name** — the lighter second word (default: *EPPM*)
4. In the **Logo Images** section, upload:
   - **Light Mode Logo** — used when the user's light/dark toggle is set to light
   - **Dark Mode Logo** — used when the toggle is set to dark
5. Save the theme; the new branding applies immediately to the sidebar header and the sign-in page for any user who has this theme active

:::tip
To roll out a brand change to every user at once, edit the **currently active theme** rather than creating a new one — every user who has that theme selected picks up the change on their next page load.
:::

:::info
Branding values (app name, logo) live on the theme record. Switching themes therefore also switches branding. If you want consistent branding across all palettes, set the same app name and logo on every custom theme you publish.
:::
