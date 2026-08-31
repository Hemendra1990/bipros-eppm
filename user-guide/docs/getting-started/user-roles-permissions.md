---
sidebar_position: 4
title: User Roles & Permissions
description: Understanding the role-based access control system in Bipros EPPM
---

# User Roles & Permissions

Bipros EPPM uses a **Role-Based Access Control (RBAC)** system. Users are assigned to **Profiles**, and each profile contains a set of **Permissions** that define what actions the user can perform across the system.

## Actor Overview

| Actor | Description | Primary Responsibilities |
|---|---|---|
| **System Administrator** | Full system access | User management, organisation setup, integration configuration, template management |
| **Programme Manager (PMO)** | Multi-project oversight | Portfolio management, cross-project reporting, programme dashboards |
| **Project Manager** | Project owner | Project creation, WBS, scheduling, cost tracking, EVM monitoring |
| **Planning Engineer** | Scheduling specialist | CPM scheduling, baseline management, schedule compression, what-if scenarios |
| **Cost Engineer** | Budget & cost control | Budget setup, BOQ management, RA bill processing, cost forecasting |
| **Site / Field Engineer** | Daily operations | DPR entry, labour returns, weather logs, equipment logs, material reconciliation |
| **Resource Manager** | Resource allocation | Labour deployment, equipment scheduling, resource curves, productivity norms |
| **Contract Manager** | Contract & payments | Contract creation, RA bill verification, payment processing |
| **Risk Manager** | Risk oversight | Risk register maintenance, Monte Carlo analysis, mitigation tracking |
| **Document Controller** | Document management | Drawing uploads, RFI management, document distribution, version control |
| **Permit Officer** | Statutory compliance | Permit application, approval tracking, regulatory compliance |
| **GIS Analyst** | Spatial data | Polygon mapping, satellite imagery, construction progress overlays |
| **Executive / Stakeholder** | Read-only oversight | Dashboards, KPIs, predictions, high-level reports |

## Profile-Based Access Control

Bipros EPPM uses **Profiles** to group permissions. A user is assigned one or more profiles, and each profile grants a specific set of permissions.

### Permission Levels

| Level | Code Suffix | Description |
|---|---|---|
| **View** | `_VIEW` | Can read and view data |
| **Create** | `_CREATE` | Can create new records |
| **Update** | `_UPDATE` | Can modify existing records |
| **Delete** | `_DELETE` | Can remove records |
| **Admin** | `_ADMIN` | Full administrative control over the module |

### Example Permission Matrix

| Module | View | Create | Update | Delete | Admin |
|---|---|---|---|---|---|
| Project | All roles | PM, Admin | PM, Admin | Admin only | Admin |
| WBS | All roles | PM, Planning | PM, Planning | PM, Admin | Admin |
| Schedule | All roles | Planning Engineer | Planning Engineer | PM, Admin | Admin |
| Cost / Budget | PM, Cost, Admin | Cost, Admin | Cost, Admin | Admin | Admin |
| DPR | PM, Site, Admin | Site Engineer | Site Engineer | Admin | Admin |
| RA Bill | Contract, Cost, Admin | Contract Manager | Contract Manager | Admin | Admin |
| Risk | All roles | Risk Manager | Risk Manager | Admin | Admin |
| Reports | All roles | Admin | Admin | Admin | Admin |
| Users / Security | Admin only | Admin | Admin | Admin | Admin |
| Integrations | Admin only | Admin | Admin | Admin | Admin |

## Project-Level Access

In addition to global permissions, Bipros EPPM supports **project-level access control**. A user can be granted access to specific projects via the **Project Member** assignment.

### Access Types per Project

| Access Type | Description |
|---|---|
| **Read** | Can view project data but not modify |
| **Write** | Can create and update project data |
| **Admin** | Full control over the project including member management |

## Managing Users (Admin Only)

System Administrators can:

1. **Create Users** — Navigate to Admin > Users > New User
2. **Assign Profiles** — Select one or more profiles for the user
3. **Set Project Access** — Add the user to specific projects with appropriate access levels
4. **Deactivate Users** — Disable login without deleting historical data
5. **Reset Passwords** — Trigger password reset emails

## Best Practices

- Assign the **minimum necessary permissions** for each user's role
- Use **project-level access** to restrict sensitive projects
- Regularly **audit user access** through the Admin panel
- Create **custom profiles** for unique organisational roles
