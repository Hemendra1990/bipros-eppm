---
sidebar_position: 2
title: Organizational Breakdown Structure (OBS)
description: Define department and team hierarchy for resource assignment and reporting
---

# Organizational Breakdown Structure (OBS)

The **Organizational Breakdown Structure (OBS)** defines your organization's department and team hierarchy. It maps who is responsible for what within the project portfolio.

## Accessing the OBS

Click **OBS** in the sidebar.

![OBS](/img/screenshots/05-obs.png)

## What Is the OBS?

The OBS is a hierarchical representation of your organization's structure. It is used to:

- **Assign responsibility** for projects and WBS elements to organizational units
- **Enable reporting** by organizational unit (e.g., "Show all activities owned by the Construction Department")
- **Control access** by mapping user roles to OBS nodes

### Example OBS Hierarchy

```
Organization
├── Chief Executive
│   ├── Engineering Division
│   │   ├── Design Department
│   │   ├── Construction Department
│   │   └── Quality Department
│   ├── Finance Division
│   │   ├── Accounts Department
│   │   └── Procurement Department
│   └── Operations Division
│       ├── Maintenance Department
│       └── Safety Department
```

## OBS Node Properties

| Field | Description |
|---|---|
| **OBS Code** | Unique identifier for this organizational node |
| **OBS Name** | Name of the department, division, or team |
| **Parent** | The parent OBS node (except for the root) |
| **Description** | Optional description of the organizational unit's responsibility |

## Creating OBS Nodes

1. Click **Add OBS Node** (or **New Node**)
2. Fill in:
   - **OBS Code** — Unique identifier
   - **OBS Name** — Department or team name
   - **Parent** — Select the parent node
3. Click **Save**

## How OBS Relates to EPS and WBS

| Structure | Organizes | Answers |
|---|---|---|
| **EPS** | Projects into enterprise groups | "What programmes do we have?" |
| **OBS** | People into organizational units | "Who is responsible?" |
| **WBS** | Work into deliverable packages | "What work needs to be done?" |

The intersection of OBS and WBS determines **responsibility** — which organizational unit is responsible for which work package.

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| OBS | Organizational Breakdown Structure |
| EPS | Enterprise Project Structure |
| WBS | Work Breakdown Structure |
