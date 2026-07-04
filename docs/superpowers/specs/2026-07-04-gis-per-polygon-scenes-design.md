# GIS per-polygon scenes, cascade delete, and viewer UX — design

Date: 2026-07-04
Status: Approved (design), pending implementation
Area: `backend/bipros-gis`, `frontend/src/app/(app)/projects/[projectId]/gis-viewer`, `frontend/src/components/gis`, `frontend/src/lib/api/gisApi.ts`

## Problem

The GIS viewer for the Khasab demo confuses users:

1. Every polygon shows the same flat list of 158 scenes — no way to pick a polygon and see only its scenes, no way to jump/zoom to a polygon.
2. Deleting a polygon leaves its scenes/images (and analysis rows and MinIO rasters) behind.
3. No way to delete several polygons at once.
4. The "Progress Tracking" tab is not wanted for the demo.
5. The Satellite Images grid is a flat, ungrouped, unfiltered wall of images.
6. Drawing a polygon offers no name — the label is only the WBS code.
7. The "Layers" tab does not show data correctly (symptom TBD — inspect live).

### Root cause (data model)

`SatelliteImage` (`gis.satellite_images`) is **global per project**, deduped by unique `sceneId`, with footprint bounds (N/S/E/W) but **no polygon FK**. The frontend filters scenes by the **union bbox of all polygons**, so selecting/zooming a single polygon does nothing and every polygon sees every scene. The only polygon↔scene link today is the async `ConstructionProgressSnapshot` analysis row (`wbsPolygonId` + `satelliteImageId` as loose UUID columns). Deleting a polygon (`WbsPolygonService.delete`) removes one row and cascades nothing.

## Decisions (locked with user)

- **Scene↔polygon model:** full per-polygon ownership. Add `wbsPolygonId` to `SatelliteImage`; ingestion clips + stores one image row per (scene × polygon). Re-ingest cost (storage × polygon count) accepted for the small Khasab set.
- **Delete cascade:** all-overlapping. Deleting a polygon deletes its owned scenes + their MinIO rasters + its analysis snapshots, and also legacy (null-polygon) images whose footprint overlaps the polygon.
- **Polygon name:** add an optional free-text `name`, defaulting to the attached WBS node's name.
- **Layers tab:** inspect the running app and fix per finding.
- **T46 wrong-region tiles:** investigate; fix if the cause is bounded, else flag.

## Backend design (`bipros-gis`)

### Entities
- `SatelliteImage` (`domain/model/SatelliteImage.java`): add `wbsPolygonId` (UUID, nullable). `ddl-auto=update` adds the column in dev; add a Liquibase changeset for prod (`backend/bipros-api/src/main/resources/db/changelog/`).
- `WbsPolygon` (`domain/model/WbsPolygon.java`): add `name` (String, nullable).

### Ingestion (`application/service/SatelliteIngestionService.java`)
- `run(...)` currently dedups by `sceneId` across all polygons (`seenThisRun`, `existsBySceneId`). Change to **per-polygon**: a scene is fetched/clipped/persisted once **per polygon** it overlaps.
  - Dedup within a run keyed by `(sceneId, polygonId)`.
  - Cross-run dedup keyed by `(sceneId, polygonId)` → new repo check `existsBySceneIdAndWbsPolygonId`.
  - Raster storage key becomes `{projectId}/{polygonId}/{year}/{month}/{sanitisedSceneId}.tif` so per-polygon clips don't clobber each other.
  - `persistImage(...)` gains a `polygonId` param and stamps `wbsPolygonId` (in addition to existing `projectId`, `layerId`).

### Delete cascade (`application/service/WbsPolygonService.java`)
`delete(projectId, polygonId)`:
1. Load + ownership-check the polygon (unchanged).
2. Delete owned images: `imageRepository.findByWbsPolygonId(polygonId)` → for each, `rasterStorage.delete(URI.create(filePath))` (best-effort, swallow storage errors), then delete rows.
3. Delete legacy overlapping images: `wbsPolygonId IS NULL` and footprint bbox intersects the polygon's bbox (compute from `WbsPolygon.polygon` envelope) → delete rows + rasters.
4. Delete `ConstructionProgressSnapshot` rows via `findByWbsPolygonId(polygonId)`.
5. Delete the polygon row.
Wrap in `@Transactional`; raster deletes are best-effort and logged.

`bipros-gis` must be able to reach `RasterStorage` — already a dependency via `bipros-integration` (used by the ingestion + thumbnail paths).

### Batch delete (multi-select) — `api/WbsPolygonController.java`
- `POST /v1/projects/{projectId}/gis/polygons/batch-delete` body `{ "ids": [uuid, …] }` → service iterates the single cascade per id, returns count deleted. Same `@PreAuthorize` as the existing delete.

### DTOs & repositories
- `WbsPolygonRequest`/`WbsPolygonResponse`: add `name`.
- `SatelliteImageResponse`/`SatelliteImageRequest`: add `wbsPolygonId`.
- `SatelliteImageRepository`: add `findByWbsPolygonId`, `existsBySceneIdAndWbsPolygonId`, and a legacy-overlap query (or fetch project images and filter by bbox in service — simpler, avoids PostGIS spatial SQL).
- `ConstructionProgressSnapshotRepository.findByWbsPolygonIdOrderByCaptureDate` already exists — reuse for cascade.

### T46 wrong-region investigation
Screenshot scenes `T46RDV/REV/SEA` are UTM zone 46 (~74°E, India/Nepal) on a zone-40 Oman project (~56°E). Inspect `SentinelHubAdapter.findImagery(polygon, …)` and how the AOI bbox / lat-lon order is passed to the vendor. If a swapped lat/lon or an over-wide default bbox is the cause and the fix is bounded, fix it; otherwise document and leave.

## Frontend design (`gis-viewer`)

### New `PolygonListPanel` (`components/gis/PolygonListPanel.tsx`)
Rendered in Map-Viewer **View mode** right column (above `LayerControlPanel`). Rows: polygon `name` (fallback `wbsCode`), area, and scene count (from `allScenes` grouped by `wbsPolygonId`). Behavior:
- Click a row → set `selectedPolygonId` (new page state) → map fits to that polygon's extent and highlights it; scene picker filters to that polygon.
- Checkbox per row + "Select all" → multi-select set. "Delete selected (N)" button → confirm dialog ("Deletes N polygons and their satellite scenes — cannot be undone") → `gisApi.batchDeletePolygons`.
- A "Show all scenes / clear selection" control to reset `selectedPolygonId`.

### Page state & filtering (`gis-viewer/page.tsx`)
- Add `selectedPolygonId: string | null`.
- `relevantScenes`: when a polygon is selected, `allScenes.filter(s => s.wbsPolygonId === selectedPolygonId)`; else all (drop the union-bbox filter as the primary path; keep a bbox fallback only for legacy null-polygon scenes if needed).
- Map fit: when `selectedPolygonId` changes, fit to that one polygon's extent (reuse `computeGeoJsonExtent4326` on the single feature) and pass a `highlightId` to `MapViewer`.

### Draw with name (`components/gis/DrawReviewPanel.tsx`)
- Add a name text input, default = selected WBS node's `name`. Include `name` in the `createPolygon` payload.
- `MapViewer` vector label (`MapViewer.tsx` style) shows `props.name ?? props.wbsCode`.

### Satellite Images grid (`components/gis/SatelliteImageGallery.tsx` + page satellite tab)
- Group images by `wbsPolygonId` with a section header per polygon (name + count); legacy null → "Unassigned".
- Add a polygon filter dropdown (All / each polygon / Unassigned) — pass the polygon list into the gallery.
- Wire the existing From/To date inputs to the grid query (today they only drive ingestion): filter the displayed images by `captureDate` client-side, or pass `from`/`to` to `getSatelliteImages`.

### Hide Progress Tracking tab
- Remove `{ id: "progress", label: "Progress Tracking" }` from the `tabs` array and its tab body in `page.tsx`. Keep `ProgressVarianceTable` and the API code in place (hide, not delete).

### API client (`lib/api/gisApi.ts`)
- `SatelliteImage` type: add `wbsPolygonId?: string`.
- `WbsPolygon` request/response types: add `name?: string`.
- Add `batchDeletePolygons(projectId, ids)` → `POST /polygons/batch-delete`.

## Data / migration
- Existing 158 scenes have `wbsPolygonId = null` → appear under "Unassigned" in the grid and are excluded from per-polygon scene lists until re-ingested. Re-running ingestion per polygon populates ownership. No destructive backfill.
- Dev: `ddl-auto=update` adds `wbs_polygon_id` and `name`. Prod: add Liquibase changesets.

## Success criteria / verification
1. Selecting a polygon in the list zooms the map to it and the scene picker shows only that polygon's scenes.
2. After re-ingestion, the Satellite Images grid is grouped by polygon with a working polygon + date filter.
3. Deleting a polygon (single or batch) removes its scene rows, their MinIO rasters, and its analysis snapshots — verified by DB + storage check.
4. Drawing a polygon accepts a name; the map label and polygon list show it.
5. The Progress Tracking tab is gone from the tab bar.
6. The Layers tab shows correct data (per live finding).
7. Backend compiles (`mvn -pl bipros-gis install`), frontend builds/lints, viewer loads without console errors.

## Out of scope
- Re-associating legacy scenes without re-ingest (no destructive backfix).
- Currency/other tabs.
- Flutter app (read-only per project convention).
