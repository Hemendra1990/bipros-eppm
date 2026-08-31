---
sidebar_position: 2
title: Navigating the Application
description: Learn the layout — sidebar, header, project tabs, and how to find every module
---

# Navigating the Application

Bipros EPPM uses a dark-themed interface with a persistent sidebar on the left, a header bar at the top, and a scrollable content area in the centre.

## Login Screen

When you first open the application, you are presented with the **Sign In** page. The page uses an editorial split-screen layout designed to set context before you even sign in.

![Login Screen](/img/screenshots/00-login.png)

### Layout

| Region | Contents |
|---|---|
| **Top navigation bar** | Branded logo and app name (configurable per organisation — see [Branding](../admin/settings#branding-app-name--logo)) and supporting links. |
| **Left side — editorial hero** | Headline, supporting copy, and a trust strip listing reference customers. |
| **Right side — sign-in card** | Username, Password (with show/hide toggle), **Remember me** checkbox, and the **Sign in** button. |
| **Live programme telemetry strip** | Below the hero, a live mini-Gantt and KPI tiles (active programmes, at-risk programmes, schedule adherence, activities tracked) preview the kind of canvas the user lands on after signing in. |
| **Features grid** | A four-card section beneath the telemetry strip summarising the platform's core capabilities. |
| **Footer** | Multi-column footer with product, company, and legal links. |

The sign-in page renders in a fixed light theme regardless of the user's in-app light/dark preference, to keep the marketing rhythm consistent.

### Sign-in fields

| Field | Description |
|---|---|
| **Username** | Your assigned username (e.g., `admin`) |
| **Password** | Your account password |
| **Remember me** | When checked, your session is preserved across browser restarts |
| **Sign in** | Submits your credentials and redirects you to the Dashboard on success |

After successful authentication, you are redirected to the **Home Dashboard** (or to the page captured in the `next` query parameter if you arrived via a deep link).

---

## Application Layout

The application is divided into three regions:

![Dashboard — Main Layout](/img/screenshots/01-dashboard.png)

### 1. Sidebar (Left)

The vertical sidebar provides navigation to every major module. Icons are always visible; hovering or expanding the sidebar reveals labels.

| Icon Position | Module | Description |
|---|---|---|
| 1 | **Dashboard** | Home page with project status summary and quick actions |
| 2 | **Projects** | List of all projects; click to enter a project's detail view |
| 3 | **Portfolios** | Group and manage related projects as portfolios |
| 4 | **EPS** | Enterprise Project Structure — top-level organizational hierarchy |
| 5 | **Resources** | Labour, equipment, and material resource management |
| 6 | **Calendars** | Working-time calendar definitions and holiday configuration |
| 7 | **Reports** | S-curves, resource histograms, cash flow, and custom reports |
| 8 | **Risk** | Enterprise-wide risk register |
| 9 | **OBS** | Organizational Breakdown Structure — department and team hierarchy |
| 10 | **Analytics** | AI-powered natural-language project analytics |
| 11 | **Dashboards** | Multi-tier dashboard hub (Executive, Programme, Operational, Field) |
| 12 | **Settings** | Global system configuration (Admin only) |
| 13 | **WBS Templates** | Reusable Work Breakdown Structure templates (Admin only) |
| 14 | **Integrations** | Government system integration configuration (Admin only) |

### 2. Header Bar (Top)

The header displays:

- **Application title**: "Enterprise Project Portfolio Management"
- **User menu** (top right): Shows the logged-in user with a **Logout** button

### 3. Content Area (Centre)

The main workspace where pages, forms, tables, and charts are rendered. The content area scrolls independently of the sidebar.

---

## Project Detail Navigation

When you click on a project from the Projects list, you enter the **Project Detail** view. This view has its own horizontal tab bar below the header.

![Project Detail — Tab Navigation](/img/screenshots/20-project-overview.png)

### Primary Tabs

These tabs are always visible in the tab bar:

| Tab | Description |
|---|---|
| **Overview** | Project summary — status, dates, priority, and key metrics |
| **WBS** | Work Breakdown Structure tree for this project |
| **Activities** | Activity list with scheduling data, durations, and dates |
| **Gantt** | Gantt chart visualization of the project schedule |
| **Resources** | Resources assigned to this project |
| **Costs** | Cost breakdown and budget tracking |
| **EVM** | Earned Value Management — PV, EV, AC, CPI, SPI charts |

### Secondary Tabs (via direct navigation)

These pages are accessible from sidebar links within the project context or via the **More** dropdown menu:

| Tab | Description |
|---|---|
| **Contracts** | Contract management — vendor details, LOA dates, LD rates |
| **Documents** | Project document library with folder hierarchy |
| **GIS** | Geographic Information System — map viewer with project locations |

### More Menu Items

Additional modules accessible from the **More (...)** dropdown:

| Menu Item | Description |
|---|---|
| **Schedule Health** | Float distribution analysis and schedule health scoring |
| **Schedule Compression** | Fast-tracking and crashing analysis tools |
| **Risk Analysis** | Project-level risk register and Monte Carlo simulations |
| **Predictions** | AI-driven project forecasting |
| **RA Bills** | Running Account bills — billing periods, gross/net amounts |
| **Drawings** | Engineering drawing management and version control |
| **RFIs** | Requests for Information — tracking and response management |
| **Equipment Logs** | Equipment deployment, operating hours, and fuel consumption |
| **Labour Returns** | Daily labour deployment and man-day calculations |
| **Materials** | Material reconciliation — stock, consumption, and wastage |

---

## Quick Navigation Tips

- **Click any sidebar icon** to jump directly to that module
- **Use the browser's back/forward buttons** to navigate between pages
- **Project context is preserved** — when you navigate within a project, the sidebar highlights the current project
- **The breadcrumb trail** at the top of some pages shows your current location in the hierarchy
