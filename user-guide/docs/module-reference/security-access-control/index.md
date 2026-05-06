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
