# GIS per-polygon scenes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make satellite scenes owned per-polygon so the viewer can filter/zoom/group/cascade-delete by polygon, add polygon naming and multi-delete, hide Progress Tracking, and fix the Layers tab.

**Architecture:** Add `wbsPolygonId` to `SatelliteImage`; ingestion clips + persists one image per (scene × polygon). Frontend gains a polygon list that drives a `selectedPolygonId` used to filter scenes, fit the map, and group the gallery. Delete cascades images + rasters + snapshots; a batch endpoint deletes several polygons at once.

**Tech Stack:** Spring Boot 3.5 / Java 23 (`bipros-gis`), PostGIS + MinIO, Next.js 16 / React 19 / OpenLayers, React Query, axios (`gisApi.ts`).

## Global Constraints

- Every backend response is wrapped in `ApiResponse<T>` (from `bipros-common`).
- Backend endpoints under `/v1/projects/{projectId}/gis/...`; keep existing `@PreAuthorize` on new endpoints matching sibling methods.
- Dev DB uses `ddl-auto=update` (additive only) — new columns must be **nullable**. Prod uses `validate` + Liquibase — add a changeset under `backend/bipros-api/src/main/resources/db/changelog/`.
- Frontend money/format rules are irrelevant here; do NOT touch currency code.
- Match existing file style/patterns. Surgical changes only. Keep `ProgressVarianceTable` + progress API code (hide the tab, don't delete code).
- Build check backend: `cd backend && mvn -q -pl bipros-gis install` (per repo memory, install the module, not just run the aggregator). Frontend build: `cd frontend && node node_modules/next/dist/bin/next build` (corepack bug — don't use `pnpm` bin directly).

---

## Phase 1 — Backend model, ownership, cascade

### Task 1: `SatelliteImage.wbsPolygonId` + repo + DTO

**Files:**
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/domain/model/SatelliteImage.java`
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/domain/repository/SatelliteImageRepository.java`
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/application/dto/SatelliteImageResponse.java`
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/application/dto/SatelliteImageRequest.java`

**Interfaces produced (later tasks rely on these exact names):**
- Entity field `UUID wbsPolygonId` + getter/setter.
- Repo: `List<SatelliteImage> findByWbsPolygonId(UUID wbsPolygonId)`, `boolean existsBySceneIdAndWbsPolygonId(String sceneId, UUID wbsPolygonId)`.
- `SatelliteImageResponse.wbsPolygonId` populated in its mapper/`fromEntity`.

- [ ] Read the entity; add `@Column(name = "wbs_polygon_id") private UUID wbsPolygonId;` (nullable) next to `layerId`, with getter/setter following the file's accessor style.
- [ ] Add the two repository methods above. Do NOT write a `(:p is null or …)` JPQL filter (Postgres nullable-param cast gotcha) — derived queries only.
- [ ] Add `wbsPolygonId` to `SatelliteImageResponse` (+ its `fromEntity`/builder) and `SatelliteImageRequest`.
- [ ] Verify: `mvn -q -pl bipros-gis install` compiles.
- [ ] Commit: `feat(gis): add wbsPolygonId ownership to SatelliteImage`.

### Task 2: `WbsPolygon.name` + DTOs

**Files:**
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/domain/model/WbsPolygon.java`
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/application/dto/WbsPolygonRequest.java`
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/application/dto/WbsPolygonResponse.java`
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/application/service/WbsPolygonService.java` (create/update mapping)

**Interfaces produced:** Entity `String name` + getter/setter; `WbsPolygonRequest.name`; `WbsPolygonResponse.name`.

- [ ] Add `@Column(name = "name") private String name;` (nullable) to the entity + accessors.
- [ ] Add `name` to request/response DTOs; map it in `WbsPolygonService.create` and `update` (set `name` from request).
- [ ] Verify compile + commit: `feat(gis): add optional name to WbsPolygon`.

### Task 3: Per-polygon ingestion

**Files:**
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/application/service/SatelliteIngestionService.java`

**Consumes:** repo `existsBySceneIdAndWbsPolygonId` (Task 1). **Produces:** images stamped with `wbsPolygonId`, raster keys namespaced by polygon.

- [ ] Read `run(...)` (~lines 78-188). Change dedup so a scene is persisted **once per polygon**:
  - within-run set keyed by `sceneId + "|" + polygonId` (not just `sceneId`);
  - cross-run guard: replace `imageRepository.existsBySceneId(...)` with `imageRepository.existsBySceneIdAndWbsPolygonId(desc.sceneId(), polygon.getId())`.
- [ ] Change the storage key (currently `{projectId}/{year}/{month}/{scene}.tif`) to `{projectId}/{polygonId}/{year}/{month}/{sanitisedSceneId}.tif`.
- [ ] `persistImage(...)`: add a `UUID polygonId` param; set `image.setWbsPolygonId(polygonId)`. Update the call site (`persistImage(projectId, polygon.getLayerId(), desc, …)`) to also pass `polygon.getId()`.
- [ ] Verify compile. Manual verification deferred to Phase 4 (re-ingest on running stack).
- [ ] Commit: `feat(gis): ingest and store satellite scenes per polygon`.

### Task 4: Cascade delete + batch delete

**Files:**
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/application/service/WbsPolygonService.java`
- Modify: `backend/bipros-gis/src/main/java/com/bipros/gis/api/WbsPolygonController.java`
- Possibly Create: `backend/bipros-gis/src/main/java/com/bipros/gis/application/dto/BatchDeleteRequest.java` (`{ List<UUID> ids }`)

**Consumes:** `SatelliteImageRepository.findByWbsPolygonId` (Task 1), `RasterStorage` (from `bipros-integration`, already used by ingestion), `ConstructionProgressSnapshotRepository.findByWbsPolygonIdOrderByCaptureDate` (exists).

- [ ] Inject `RasterStorage`, `SatelliteImageRepository`, `ConstructionProgressSnapshotRepository` into `WbsPolygonService` (follow the existing constructor-injection style).
- [ ] Rewrite `delete(projectId, polygonId)` as `@Transactional`:
  1. Load + ownership-check polygon (keep existing check).
  2. Owned images: `findByWbsPolygonId(polygonId)` → best-effort `rasterStorage.delete(URI.create(img.getFilePath()))` in try/catch (log + continue), then `imageRepository.deleteAll(owned)`.
  3. Legacy overlap: fetch project images (`findByProjectIdOrderByCaptureDate(projectId)`), keep those with `wbsPolygonId == null` whose bbox (`westBound/southBound/eastBound/northBound`) intersects the polygon envelope (compute from `polygon.getPolygon().getEnvelopeInternal()`); delete rows + rasters same as above.
  4. Snapshots: `snapshotRepo.findByWbsPolygonIdOrderByCaptureDate(polygonId)` → `deleteAll`.
  5. Delete the polygon row.
- [ ] Add `deleteBatch(UUID projectId, List<UUID> ids)` → loop `delete(projectId, id)`; return count.
- [ ] Controller: add `POST /batch-delete` accepting `BatchDeleteRequest`, same `@PreAuthorize` as the existing `DELETE /{polygonId}`, returning `ApiResponse<Integer>` (count). 
- [ ] Verify compile + commit: `feat(gis): cascade-delete scenes/rasters/snapshots on polygon delete + batch delete`.

### Task 5: T46 wrong-region investigation (timeboxed)

**Files:** inspect `backend/bipros-integration/src/main/java/com/bipros/integration/adapter/satellite/SentinelHubAdapter.java` (`findImagery`) and how the AOI/bbox + lat-lon order is sent to Sentinel Hub.

- [ ] Determine why zone-46 (India) tiles return for a zone-40 (Oman) AOI. Check: lat/lon vs lon/lat order in the request bbox; whether a project-wide/default bbox is used instead of the polygon footprint; CRS.
- [ ] If the cause is a bounded bug (order/CRS/wrong-extent) → fix + note in commit. If it needs vendor-side query redesign → document findings in the spec and leave. Commit only if fixed: `fix(gis): constrain Sentinel Hub query to polygon AOI`.

### Task 6: Prod Liquibase changeset

**Files:** Create a changeset under `backend/bipros-api/src/main/resources/db/changelog/` (follow the existing changelog include pattern).

- [ ] Add columns `gis.satellite_images.wbs_polygon_id UUID NULL` and `gis.wbs_polygons.name VARCHAR NULL`. Wire it into the master changelog include list.
- [ ] Commit: `chore(db): changeset for wbs_polygon_id and polygon name`.

---

## Phase 2 — Frontend API + page state (depends on Phase 1 contracts, not builds)

### Task 7: `gisApi` types + batch delete

**Files:** Modify `frontend/src/lib/api/gisApi.ts`

**Interfaces produced:** `SatelliteImage.wbsPolygonId?: string`; polygon request/response `name?: string`; `batchDeletePolygons(projectId, ids: string[]): Promise<ApiResponse<number>>` → `POST /polygons/batch-delete` `{ ids }`.

- [ ] Add `wbsPolygonId?: string` to the satellite image type; `name?: string` to polygon request + response types.
- [ ] Add `batchDeletePolygons`. Follow the existing function/typing style in the file.
- [ ] Commit: `feat(gis-ui): api types for polygon ownership + batch delete`.

### Task 8: Page state — selection, filtering, map fit, hide tab

**Files:** Modify `frontend/src/app/(app)/projects/[projectId]/gis-viewer/page.tsx`

**Consumes:** Task 7 types. **Produces (props for Phase 3):** `selectedPolygonId`, `setSelectedPolygonId`, `polygons` list, `sceneCountByPolygon` map, `handleBatchDelete`.

- [ ] Add state `const [selectedPolygonId, setSelectedPolygonId] = useState<string | null>(null)`.
- [ ] Replace the union-bbox `relevantScenes` primary path: when `selectedPolygonId`, `relevantScenes = allScenes.filter(s => s.wbsPolygonId === selectedPolygonId)`; else all scenes. Keep the `showAllScenes` escape hatch for legacy null-polygon scenes.
- [ ] On `selectedPolygonId` change, fit the map to that single polygon's extent (reuse `computeGeoJsonExtent4326` over the one feature) via the existing `fitSignal` mechanism, and pass `highlightId={selectedPolygonId}` to `MapViewer`.
- [ ] Add `handleBatchDelete(ids: string[])` mutation → `gisApi.batchDeletePolygons`; on success invalidate `["gis", projectId, "geojson"]` + `["gis", projectId, "satellite-images"]`, clear selection.
- [ ] Remove the `progress` entry from the `tabs` array (page.tsx:430-435) and its tab body (page.tsx:694). Leave imports/component in place if still referenced elsewhere; otherwise remove only the now-orphaned `progress` import.
- [ ] Wire `PolygonListPanel` (Task 9) into the View-mode right column above `LayerControlPanel`.
- [ ] Verify: `next build` compiles. Commit: `feat(gis-ui): per-polygon selection, scene filter, hide progress tab`.

---

## Phase 3 — Frontend components (depend on Phase 2 props/types)

### Task 9: `PolygonListPanel`

**Files:** Create `frontend/src/components/gis/PolygonListPanel.tsx`; wire in page.tsx (Task 8).

**Consumes:** `polygons` (from geojson features), `selectedPolygonId`, `setSelectedPolygonId`, `sceneCountByPolygon`, `onBatchDelete`.

- [ ] Rows: polygon `name ?? wbsCode`, area, scene count. Click row → `setSelectedPolygonId(id)` (toggle off if re-clicked).
- [ ] Checkbox per row + "select all"; "Delete selected (N)" → `window.confirm("Delete N polygon(s) and their satellite scenes? Cannot be undone.")` → `onBatchDelete(ids)`.
- [ ] "Show all / clear selection" resets `selectedPolygonId` to null.
- [ ] Match the styling of `LayerControlPanel`/`GisLayerList`. Commit: `feat(gis-ui): polygon list with click-to-zoom and multi-delete`.

### Task 10: Draw with name

**Files:** Modify `frontend/src/components/gis/DrawReviewPanel.tsx`; verify the payload path in page.tsx `createPolygon`.

- [ ] Add a name text input defaulting to the selected WBS node's `name` (from `WbsNodePicker` selection). Include `name` in the create payload passed up.
- [ ] Commit: `feat(gis-ui): name a polygon while drawing`.

### Task 11: MapViewer highlight + label

**Files:** Modify `frontend/src/components/gis/MapViewer.tsx`

- [ ] Vector `Text` label: show `props.name ?? props.wbsCode` (currently `wbsCode` only).
- [ ] Accept a `highlightId` prop; style the matching feature with a thicker/accent stroke so the selected polygon stands out.
- [ ] Commit: `feat(gis-ui): highlight selected polygon + show name label`.

### Task 12: Satellite gallery grouping + filters

**Files:** Modify `frontend/src/components/gis/SatelliteImageGallery.tsx` and the satellite tab block in page.tsx.

**Consumes:** `polygons` (id→name), `images` (with `wbsPolygonId`).

- [ ] Group images by `wbsPolygonId`; section header per polygon (name + count); `null` → "Unassigned".
- [ ] Add a polygon filter dropdown (All / each polygon / Unassigned).
- [ ] Wire the existing From/To date inputs to filter the displayed grid (client-side by `captureDate`), in addition to their current ingestion role.
- [ ] Commit: `feat(gis-ui): group satellite images by polygon + polygon/date filters`.

### Task 13: Layers tab fix (live)

**Files:** `frontend/src/components/gis/GisLayerList.tsx` (+ backend layer source if the data itself is wrong).

- [ ] With the stack running, open the Layers tab against the demo project; capture the actual vs. expected. Diagnose (empty / mismatched visible-opacity-order / duplicates / stale).
- [ ] Fix the identified defect (frontend rendering, or the `getLayers` query / seed if the data is wrong). Commit: `fix(gis-ui): correct Layers tab data`.

---

## Phase 4 — Verification (running stack)

- [ ] Bring up the stack (docker: postgres + minio + clickhouse per repo memory; backend `mvn -pl bipros-gis install` then run api; frontend dev).
- [ ] Re-run ingestion for the Khasab project polygons → confirm new `satellite_images` rows carry `wbs_polygon_id` and rasters land under `{projectId}/{polygonId}/…`.
- [ ] Select a polygon → map zooms to it, scene picker shows only its scenes.
- [ ] Satellite grid grouped by polygon; polygon + date filters work; legacy scenes under "Unassigned".
- [ ] Draw a polygon with a name → label + list show it.
- [ ] Delete a polygon (single + batch) → its `satellite_images` rows gone, MinIO objects gone (spot-check), snapshots gone.
- [ ] Progress Tracking tab absent. Layers tab correct.

---

## Self-review notes
- Spec coverage: items 1(T8/T9/T11), 2(T4), 3(T4/T9), 4(T8), 5(T12), 6(T2/T10), 7(T13); T46 = T5; legacy data = "Unassigned" (T8/T12); prod migration = T6. All covered.
- Type consistency: `wbsPolygonId` (camel) FE ↔ `wbs_polygon_id` (snake) DB; `name` both. `batchDeletePolygons` ↔ `POST /batch-delete` ↔ `deleteBatch`. Consistent.
- Nullable-JPQL gotcha avoided (derived queries only). Maven install-then-run + next-bin build noted.
