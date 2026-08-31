---
sidebar_position: 1
title: Permits — Deep Dive
description: Technical reference for permit lifecycle management, templates, and compliance tracking
---

# Permits — Deep Dive

## Overview

The Permits module manages statutory and regulatory permits required for construction projects, from application through approval to renewal.

## Actors & Roles

| Actor | Role |
|---|---|
| **Permit Officer** | Creates applications, tracks status, responds to queries |
| **Project Manager** | Monitors permit dashboard, escalates delays |
| **Admin** | Configures permit templates and workflows |

## Use Cases

### UC-PMT-01: Create Permit Template

| Attribute | Value |
|---|---|
| **ID** | UC-PMT-01 |
| **Name** | Create Permit Template |
| **Actor** | Admin |
| **Precondition** | Admin has template configuration permission |
| **Trigger** | User clicks "New Template" |

**Main Flow:**
1. User names the template (e.g., "Building Permission")
2. User defines checklist items
3. User sets default lead time
4. User configures approval workflow
5. System saves template for reuse

**Postcondition:** Template is available for permit creation |

### UC-PMT-02: Track Permit Status

| Attribute | Value |
|---|---|
| **ID** | UC-PMT-02 |
| **Name** | Track Permit Status |
| **Actor** | Permit Officer |
| **Precondition** | Permit application exists |
| **Trigger** | User opens permit detail |

**Main Flow:**
1. System displays current status
2. System shows status history timeline
3. System alerts if permit is overdue
4. System shows days remaining until expiry

**Postcondition:** Status is visible and tracked |

## Permit Lifecycle States

```
DRAFT → SUBMITTED → UNDER_REVIEW → QUERY_RAISED → UNDER_REVIEW → APPROVED → ACTIVE → EXPIRED
                              ↓
                           REJECTED
```

## Related Modules

- [Permit Task Guide](../../task-guides/managing-permits)
- [Projects](../../projects/overview)
