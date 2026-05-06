---
sidebar_position: 10
title: Managing Permits
description: How to apply for, track, and manage statutory permits in Bipros EPPM
---

# Managing Permits

Statutory permits are critical for construction and infrastructure projects. This guide covers the complete permit lifecycle from application to approval.

## Prerequisites

- Permit templates are configured (Admin > Permit Templates)
- You have `PERMIT_CREATE` permission (Permit Officer or Project Manager)
- Project is in `PLANNED` or `ACTIVE` status

## Understanding Permit Types

Common permit types in Bipros EPPM:

| Permit Type | Authority | Typical Lead Time |
|---|---|---|
| **Building Permission** | Municipal Corporation | 30–60 days |
| **Environmental Clearance** | MoEF / State PCB | 90–180 days |
| **Forest Clearance** | Ministry of Environment | 120–365 days |
| **Mining Lease** | State Mining Department | 60–120 days |
| **Road Cutting** | PWD / Municipal Corp | 15–30 days |
| **Labour License** | Labour Department | 15–30 days |
| **Fire NOC** | Fire Department | 15–30 days |

## Step 1: Create a Permit Application

1. Navigate to your project
2. Click the **Permits** tab
3. Click **New Permit**
4. Select:
   - **Permit Type** — From the template list
   - **Permit Template** — Pre-configured checklist and workflow

## Step 2: Fill in Application Details

| Field | Description |
|---|---|
| **Application Number** | Auto-generated or manual |
| **Application Date** | Date of submission |
| **Authority** | Issuing government department |
| **Expected Approval Date** | Target date based on lead time |
| **Remarks** | Any special conditions |

## Step 3: Complete Checklist Items

Each permit template includes a checklist of required documents:

| Checklist Item | Status | Remarks |
|---|---|---|
| Site Plan | ☐ | Upload PDF |
| Structural Drawings | ☐ | Upload PDF |
| Soil Test Report | ☐ | Upload PDF |
| Fire Safety Plan | ☐ | Upload PDF |

Upload documents directly against each checklist item.

## Step 4: Submit for Approval

1. Ensure all checklist items are complete
2. Click **Submit**
3. The permit status changes to **SUBMITTED**

## Step 5: Track Approval Status

Monitor the permit through its lifecycle:

| Status | Meaning |
|---|---|
| **DRAFT** | Application being prepared |
| **SUBMITTED** | Sent to authority |
| **UNDER REVIEW** | Authority is processing |
| **QUERY RAISED** | Authority has requested additional information |
| **APPROVED** | Permit granted |
| **REJECTED** | Permit denied (record reason) |
| **EXPIRED** | Permit validity has lapsed |

## Step 6: Respond to Queries

1. When status is **QUERY RAISED**, open the permit
2. Review the query from the authority
3. Upload additional documents or clarifications
4. Click **Respond to Query**
5. Status returns to **UNDER REVIEW**

## Step 7: Record Approval

1. When approved, upload the **Approval Letter / Certificate**
2. Record:
   - **Approval Number** — Permit reference number
   - **Approval Date** — Date of issue
   - **Valid From** — Start of validity
   - **Valid Until** — Expiry date
   - **Conditions** — Any conditions attached to the permit

## Permit Dashboard

The **Permit Dashboard** provides a project-wide view:

- Total permits by status
- Overdue permits (past expected approval date)
- Permits expiring in the next 30/60/90 days
- Approval timeline analytics

## Expected Outcome

- All required permits are tracked in the system
- Documents are attached and version-controlled
- Approval status is visible to the project team
- Expiry alerts prevent lapses

## Related Documentation

- [Permits](../module-reference/permits/)
- [GIS Viewer](../projects/gis-viewer)
