---
sidebar_position: 1
title: Security & Access Control — Deep Dive
description: Technical reference for RBAC, profiles, users, and project-level access
---

# Security & Access Control — Deep Dive

## Overview

Bipros EPPM implements Role-Based Access Control (RBAC) using **Profiles** (role groups) and **Permissions** (fine-grained access rights). Users are assigned to profiles, and profiles grant permissions.

## Actors & Roles

| Actor | Role |
|---|---|
| **System Administrator** | Full system access, user management, profile configuration |
| **Security Manager** | Manages profiles and permissions (if separate from Admin) |

## Core Concepts

### User

A person who can log in to Bipros EPPM. Each user has:
- Username and password
- Email address
- Status (Active / Inactive / Locked)
- Assigned profiles

### Profile

A collection of permissions. Examples:
- **Project Manager Profile** — PROJECT_CREATE, PROJECT_UPDATE, SCHEDULE_VIEW, etc.
- **Site Engineer Profile** — DPR_CREATE, DPR_UPDATE, EQUIPMENT_LOG_CREATE, etc.
- **Executive Profile** — DASHBOARD_VIEW, REPORT_VIEW, PROJECT_VIEW

### Permission Profiles (Admin UI)

The **Permission Profiles** page (`Admin → Permission Profiles`) is the single place where the catalogue of profiles is managed. Each profile is a reusable bundle of fine-grained permissions that can be assigned to users.

| Field | Description |
|---|---|
| **Code** | Unique, immutable identifier. Letters, digits, underscores only (e.g. `SAFETY_OFFICER`). Cannot be changed after creation. |
| **Name** | Human-readable label shown to administrators (e.g. *Site Safety Officer*). |
| **Description** | Optional free-text summary of the profile's intent. |
| **Maps to legacy role** | The legacy role name (e.g. `PROJECT_MANAGER`, `SCHEDULER`, `VIEWER`) added to the user's JWT when this profile is assigned. Required so existing role-based authorisation continues to work alongside the permission system. |
| **Permissions** | The set of permissions this profile grants. The catalogue is grouped by module with **Select all / Unselect all** helpers per module and a global **Select all / Clear all** for the whole profile. |
| **System default** | Read-only flag. System defaults (the seeded baseline profiles such as `ADMIN`, `EXECUTIVE`, etc.) cannot be deleted, but their permission set can still be edited. |

**Per-user assignment.** Each user is assigned **exactly one** permission profile. Profile assignment is performed from `Admin → Users` by clicking the profile badge on the user's row, choosing a profile from the picker, and saving. A user can also be set to *No profile*, in which case they retain only the permissions granted by their legacy role (if any). When the assignment is saved, the backend re-issues the user's JWT with the new role and permissions on the next token refresh.

:::info
Profiles are **not** additive across multiple assignments — a user has one active profile at a time. To grant a user the union of two profiles' rights, create a third profile that combines them, or extend one of the existing profiles.
:::

**How profiles compose with project-level access.** A user's effective rights for a given project are the **intersection** of:
1. the global permissions granted by their assigned profile, and
2. the project-level access level (Read / Write / Admin) recorded on their project membership.

For example, a user whose profile grants `SCHEDULE_UPDATE` will only be able to edit the schedule of projects where they have **Write** or **Admin** access; on **Read** projects, the action is hidden even though the global permission is present.

### Permission

A specific access right. Format: `{MODULE}_{ACTION}`

Examples:
- `PROJECT_CREATE` — Create new projects
- `PROJECT_UPDATE` — Edit existing projects
- `PROJECT_VIEW` — View project details
- `PROJECT_DELETE` — Delete projects
- `SCHEDULE_UPDATE` — Modify schedules
- `COST_UPDATE` — Modify budgets and costs
- `DPR_CREATE` — Create daily progress reports
- `USER_ADMIN` — Manage users and profiles

### Project-Level Access

In addition to global permissions, users can be granted access to specific projects:

| Access Level | Description |
|---|---|
| **Read** | Can view project data |
| **Write** | Can create and update project data |
| **Admin** | Full control including member management |

## Use Cases

### UC-SEC-01: Create User

| Attribute | Value |
|---|---|
| **ID** | UC-SEC-01 |
| **Name** | Create User |
| **Actor** | System Administrator |
| **Precondition** | Admin is logged in |
| **Trigger** | User clicks "New User" |

**Main Flow:**
1. Admin enters username, email, full name
2. Admin assigns one or more profiles
3. Admin sets project-level access
4. System sends welcome email with temporary password
5. System creates user record

**Postcondition:** User can log in with assigned permissions |

### UC-SEC-02: Create Profile

| Attribute | Value |
|---|---|
| **ID** | UC-SEC-02 |
| **Name** | Create Profile |
| **Actor** | System Administrator |
| **Precondition** | Admin is logged in |
| **Trigger** | User clicks "New Profile" |

**Main Flow:**
1. Admin names the profile
2. Admin selects permissions from the master list
3. Admin saves the profile
4. Profile is available for assignment to users

**Postcondition:** Profile is created with defined permissions |

### UC-SEC-03: Assign Profile to User

| Attribute | Value |
|---|---|
| **ID** | UC-SEC-03 |
| **Name** | Assign Permission Profile to User |
| **Actor** | System Administrator |
| **Precondition** | At least one custom or system-default profile exists; the target user account exists |
| **Trigger** | Admin clicks the profile badge on a user row in `Admin → Users` |

**Main Flow:**
1. Admin opens `Admin → Users`
2. Admin clicks the profile badge cell for the target user — an inline picker is shown
3. Admin selects a profile from the dropdown (or `— No profile —` to clear)
4. Admin clicks **Save**
5. System updates the user's profile assignment and re-issues their JWT with the new permissions on next refresh

**Postcondition:** User's effective permissions reflect the newly assigned profile, intersected with their project-level access on each project.

## Permission Matrix

See [Permission Matrix](../../appendices/permission-matrix) for the complete matrix.

## Authentication

- **Method:** JWT (JSON Web Token)
- **Token Expiry:** Access token expires after 15 minutes
- **Refresh Token:** Used to obtain new access tokens without re-login
- **Password Policy:** Minimum 8 characters, uppercase, lowercase, number, special character

## Audit Trail

The system logs:
- Login attempts (success and failure)
- Permission changes
- Data modifications (who, what, when)
- Profile assignments

## Related Modules

- [User Roles](../../getting-started/user-roles-permissions)
- [Admin Settings](../../admin/settings)
