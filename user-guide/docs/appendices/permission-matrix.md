---
sidebar_position: 3
title: Permission Matrix
description: Complete matrix of roles and their permissions across all modules
---

# Permission Matrix

This matrix shows which roles have which permissions across all Bipros EPPM modules.

## Legend

| Symbol | Meaning |
|---|---|
| ✅ | Granted |
| ❌ | Not granted |
| 👁️ | View only |
| ✏️ | Create / Update |
| 🗑️ | Delete |
| 👑 | Admin (full control) |

## Module: Projects

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| PROJECT_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| PROJECT_CREATE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| PROJECT_UPDATE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| PROJECT_DELETE | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| WBS_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| WBS_CREATE | 👑 | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| WBS_UPDATE | 👑 | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| WBS_DELETE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## Module: Scheduling

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| SCHEDULE_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SCHEDULE_UPDATE | 👑 | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| BASELINE_CREATE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| BASELINE_COMPARE | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SCENARIO_CREATE | 👑 | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## Module: Cost

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| COST_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| COST_UPDATE | 👑 | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| BUDGET_CHANGE | 👑 | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| RA_BILL_CREATE | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| RA_BILL_APPROVE | 👑 | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| CASHFLOW_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |

## Module: Resources

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| RESOURCE_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| RESOURCE_UPDATE | 👑 | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| DPR_CREATE | 👑 | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| DPR_VERIFY | 👑 | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| EQUIPMENT_LOG_CREATE | 👑 | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |

## Module: Risk

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| RISK_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| RISK_CREATE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| RISK_UPDATE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| MONTE_CARLO_RUN | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |

## Module: Documents

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| DOCUMENT_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| DOCUMENT_UPLOAD | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DOCUMENT_DISTRIBUTE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| RFI_CREATE | 👑 | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |

## Module: Permits

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| PERMIT_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| PERMIT_CREATE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| PERMIT_UPDATE | 👑 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## Module: GIS

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| GIS_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| GIS_EDIT | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| SATELLITE_INGEST | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## Module: Reports & Dashboards

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| REPORT_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| REPORT_CREATE | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DASHBOARD_VIEW | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| DASHBOARD_CONFIGURE | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## Module: Security & Admin

| Permission | Admin | PMO | Project Manager | Planning Engineer | Cost Engineer | Site Engineer | Resource Manager | Contract Manager | Risk Manager | Executive |
|---|---|---|---|---|---|---|---|---|---|---|
| USER_ADMIN | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| PROFILE_ADMIN | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| INTEGRATION_ADMIN | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| TEMPLATE_ADMIN | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| SETTINGS_ADMIN | 👑 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## Notes

1. **Admin** has all permissions across all modules.
2. **Project Managers** have broad access within their assigned projects.
3. **Specialist roles** (Planning Engineer, Cost Engineer, etc.) have deep access in their domain.
4. **Executives** have read-only access to dashboards and reports.
5. **Site Engineers** have limited create permissions for DPR and equipment logs.
6. Project-level access can further restrict or expand these permissions per project.
