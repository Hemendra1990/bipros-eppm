---
sidebar_position: 1
title: GIS & Satellite Imagery — Deep Dive
description: Technical reference for spatial data, polygon mapping, and satellite progress monitoring
---

# GIS & Satellite Imagery — Deep Dive

## Overview

The GIS module integrates spatial data with project management, enabling polygon-based WBS mapping, satellite imagery ingestion, and construction progress visualisation.

## Actors & Roles

| Actor | Role |
|---|---|
| **GIS Analyst** | Creates polygons, ingests satellite images, overlays progress |
| **Project Manager** | Views GIS dashboards and progress maps |
| **Planning Engineer** | Links WBS nodes to geographic areas |

## Use Cases

### UC-GIS-01: Create WBS Polygon

| Attribute | Value |
|---|---|
| **ID** | UC-GIS-01 |
| **Name** | Create WBS Polygon |
| **Actor** | GIS Analyst |
| **Precondition** | WBS node exists, GIS layer is configured |
| **Trigger** | User draws polygon on map |

**Main Flow:**
1. User selects WBS node
2. User selects GIS layer
3. User draws polygon on the map
4. System saves polygon with WBS reference
5. System links polygon to satellite imagery

**Postcondition:** Polygon is created and linked to WBS |

### UC-GIS-02: Ingest Satellite Image

| Attribute | Value |
|---|---|
| **ID** | UC-GIS-02 |
| **Name** | Ingest Satellite Image |
| **Actor** | GIS Analyst |
| **Precondition** | Satellite image is available (GeoTIFF or similar) |
| **Trigger** | User uploads satellite image |

**Main Flow:**
1. User uploads satellite image with georeferencing
2. System validates coordinate system
3. System stores image and creates tile layers
4. System overlays on project map

**Postcondition:** Satellite image is available for viewing |

### UC-GIS-03: View Construction Progress

| Attribute | Value |
|---|---|
| **ID** | UC-GIS-03 |
| **Name** | View Construction Progress Overlay |
| **Actor** | Project Manager |
| **Precondition** | Polygons exist, DPR data is available |
| **Trigger** | User opens GIS viewer |

**Main Flow:**
1. System retrieves WBS polygons
2. System calculates completion percentage per polygon from DPR
3. System colour-codes polygons (green = complete, yellow = in progress, red = not started)
4. User can toggle satellite imagery layers

**Postcondition:** Progress is visualised on map |

## Related Modules

- [GIS Viewer](../../projects/gis-viewer)
- [Projects](../../projects/overview)
