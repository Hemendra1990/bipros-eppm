---
sidebar_position: 1
title: Documents & Drawings — Deep Dive
description: Technical reference for document management, version control, and distribution
---

# Documents & Drawings — Deep Dive

## Overview

The Documents module manages project documentation including drawings, RFIs, correspondence, and general documents with version control and distribution tracking.

## Actors & Roles

| Actor | Role |
|---|---|
| **Document Controller** | Manages uploads, versions, distribution |
| **Project Manager** | Reviews and approves document distribution |
| **All Users** | View and download documents (with permissions) |

## Use Cases

### UC-DOC-01: Upload Document

| Attribute | Value |
|---|---|
| **ID** | UC-DOC-01 |
| **Name** | Upload Document |
| **Actor** | Document Controller |
| **Precondition** | Project exists, document category is defined |
| **Trigger** | User clicks "Upload Document" |

**Main Flow:**
1. User selects document category
2. User selects file to upload
3. User enters document metadata (title, revision, date)
4. System stores file in MinIO/document storage
5. System creates document record with version 1.0

**Postcondition:** Document is stored and versioned |

### UC-DOC-02: Create RFI

| Attribute | Value |
|---|---|
| **ID** | UC-DOC-02 |
| **Name** | Create Request for Information (RFI) |
| **Actor** | Site Engineer |
| **Precondition** | Project exists, question about drawings/specs |
| **Trigger** | User clicks "New RFI" |

**Main Flow:**
1. User selects related drawing or specification
2. User describes the question or clarification needed
3. User assigns to responsible party (e.g., consultant)
4. System notifies the assignee
5. Assignee responds with clarification
6. System records the response and closes the RFI

**Postcondition:** RFI is resolved and archived |

## Version Control

Documents follow semantic versioning:

| Version | Meaning |
|---|---|
| 1.0 | Initial issue |
| 1.1 | Minor revision |
| 2.0 | Major revision / reissue |

## Storage

- **Development:** Local filesystem at `./storage/documents`
- **Production:** MinIO S3-compatible object storage

## Related Modules

- [Projects](../../projects/overview)
- [GIS](../gis-satellite/)
