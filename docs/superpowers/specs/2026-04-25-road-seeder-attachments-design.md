---
title: NH-48 Road Seeder — Attachments, GIS, and Satellite Imagery
date: 2026-04-25
project: bipros-eppm
audience: backend / data-seeder maintainers
status: approved-for-planning
---

# NH-48 Road Seeder — Attachments, GIS, and Satellite Imagery

## 1. Goal

Fill the remaining gaps in the existing NH-48 road-construction demo so every tab in the project workspace renders against real data:

- **Document Register** with binary PDFs (drawings, specs, method statements, ITPs, plans)
- **Contract attachments** at every level (contract / milestone / VO / bond)
- **GIS** — `WbsPolygons` for the corridor, `SatelliteImages` for the project
- **GIS Construction Progress** — snapshot rows so the progress timeline tab is non-empty

The existing seeder (`NhaiRoadProjectSeeder`, project code `BIPROS/NHAI/RJ/2025/001`) is **not modified**. All new data is loaded by an additive companion seeder ordered after it.

## 2. Scope

In scope:

- New Spring `@Component` seeder `NhaiRoadAttachmentsSeeder` at `@Order(150)` (runs after `NhaiRoadProjectSeeder` at 140).
- Runtime PDF stub generator for the 25 documents in the xlsx "Approved Documents" register that have no real PDF in `/Volumes/Learning/road_project_test/`.
- Copying real source files (5 PDFs + 5 JPEGs) into a classpath resource folder so the seeder is reproducible without external paths.
- A new `seed-data/road-project/reports/14-drawing-register.sql` for register-row enrichment that doesn't fit cleanly via JPA.

Out of scope:

- Modifying `NhaiRoadProjectSeeder` itself, the workbook, or any existing `*.sql` file in `reports/`.
- Parsing the Risk DOCX to add new risk-template entries — `RiskTemplateSeeder` already covers all road categories and `08-risks.sql` populates 8 project risks. The DOCX is a schema/framework reference and will be uploaded as a project document instead.
- Production migrations. Dev profile uses `ddl-auto: create-drop`, so the seeder runs on every boot and is reset on every boot.

## 3. Source data inventory

Files in `/Volumes/Learning/road_project_test/`:

| File | Role | Action |
|---|---|---|
| `PMS RoadProject TestData.xlsx` | Already on classpath, parsed by main seeder | (no change) |
| `Risk Master Metadata Road Construction.docx` | Risk framework reference | Upload as a project document under `Plans` folder |
| `DRG NH48 002 Pavement Design.pdf` | Real drawing | Bind to register entry `DRG/NH48/002` |
| `SPEC NH48 002 GSB WMM.pdf` | Real spec | Bind to register entry `SPEC/NH48/002` |
| `MS NH48 003 Bituminous Paving.pdf` | Real method statement | Bind to register entry `MS/NH48/003` |
| `ITP NH48 001 Earthwork.pdf` | Real ITP | Bind to register entry `ITP/NH48/001` |
| `PLAN NH48 004 Project Quality Plan.pdf` | Real plan | Bind to register entry `PLAN/NH48/004` |
| 5× `WhatsApp Image *.jpeg` | Treated as satellite imagery (per user direction) | Upload as `SatelliteImage` scenes |

The "Approved Documents" sheet in the workbook lists 28 entries (DRG ×7, SPEC ×5, MS ×5, ITP ×5, PLAN ×6). Plus the Risk Master DOCX as one extra reference document = 29 documents total. The 5 real PDFs cover 5 register entries; the other 23 get runtime-generated stub PDFs; the DOCX is uploaded as-is.

## 4. Architecture

### 4.1 Module placement

`NhaiRoadAttachmentsSeeder` lives in `backend/bipros-api/src/main/java/com/bipros/api/config/seeder/` alongside the existing seeders. It depends on:

- Repositories: `DocumentRepository`, `DocumentFolderRepository`, `DocumentVersionRepository`, `ContractRepository`, `ContractAttachmentRepository`, `ProjectRepository`, `WbsNodeRepository`, `WbsPolygonRepository`, `SatelliteImageRepository`, `ConstructionProgressSnapshotRepository`, `GisLayerRepository`.
- Services: `DocumentStorageService` (already exists, writes binaries into `./storage/documents/`), `ContractAttachmentService` or its underlying storage helper.

A small private helper class `MinimalPdfGenerator` produces single-page PDFs as a `byte[]` using **manually-emitted PDF bytes** (no new dependency). The format is the smallest valid PDF containing one Helvetica text block — ~600 bytes per file. If maintenance burden becomes real, swap to PDFBox in a follow-up.

### 4.2 Source-file routing

Source files are copied (one-time, by the developer or a `prepare-seed-data.sh` script) into:

```
backend/bipros-api/src/main/resources/seed-data/road-project/
├── PMS RoadProject TestData.xlsx          (existing)
├── reports/                                (existing)
├── documents/                              (NEW)
│   ├── DRG-NH48-002.pdf
│   ├── SPEC-NH48-002.pdf
│   ├── MS-NH48-003.pdf
│   ├── ITP-NH48-001.pdf
│   ├── PLAN-NH48-004.pdf
│   └── Risk-Master-Metadata.docx
└── satellite/                              (NEW)
    ├── nh48-q1-2025.jpeg
    ├── nh48-q2-2025.jpeg
    ├── nh48-q3-2025.jpeg
    ├── nh48-q4-2025.jpeg
    └── nh48-q1-2026.jpeg
```

Filenames are normalised (no spaces) when copying. The seeder reads via `ClassPathResource("seed-data/road-project/documents/...")`. If a file is missing the seeder logs a warning and falls back to a generated stub for documents (or skips the satellite scene).

### 4.3 Seeder flow

```
run() {
  if (project missing) skip;                          // sentinel: project code lookup
  if (any documents already exist for this project) skip;   // idempotency
  
  resolveDefaultFolders();                            // Drawings, Specs, MS, ITPs, Plans
  ensureMissingFolders();                             // create register-aware folders
  
  for each row in "Approved Documents" sheet:
    resolveSourcePdf() ?? generateStubPdf(row)
    storeBinary() via DocumentStorageService
    insertDocument() + DocumentVersion v1
  
  for each (doc, contractAttachmentMapping):
    storeBinary() into ./storage/contracts/
    insertContractAttachment() rows for CONTRACT, MILESTONE, VO, BOND
  
  ensureGisLayer("WBS Boundaries");
  for each L2 WBS node:
    computePolygonFromChainage()                      // 20m corridor along NH-48
    insertWbsPolygon()
  
  for each satellite jpeg:
    storeBinary() into satellite-storage path
    insertSatelliteImage() with bbox = union of WBS polygons
    insertConstructionProgressSnapshot() (per L2 WBS, scene date)
}
```

### 4.4 Stub PDF generation

`MinimalPdfGenerator.render(docNo, title, category, specRef, remarks)` returns a `byte[]` containing a 1-page A4 PDF with:

- Top: `[Sample seed data — non-engineering content]` watermark
- Body: `Document No`, `Title`, `Category`, `Reference Spec/Clause`, `Project: NH-48 Rajasthan`, `Approved by: Client – NHAI`
- All text Helvetica 12pt, single column

Implementation: hand-emit the 8-object PDF skeleton (Catalog → Pages → Page → Resources/Font → Contents → xref → trailer). One static helper, ~80 lines of Java.

### 4.5 GIS polygon synthesis

NH-48 corridor through Rajasthan is approximately:
- Ch 145+000 ≈ (26.6500° N, 73.5000° E)
- Ch 165+000 ≈ (26.7800° N, 73.6500° E)

A linear interpolation along this line produces 7 contiguous segments — one per L2 WBS package. Each segment is widened by ±10 m perpendicular to the corridor heading to form a rectangular polygon. Coordinates use `org.locationtech.jts.geom.Polygon` in EPSG:4326. Center point is the segment midpoint.

The GIS Layer (`gis_layers`) entry "NH-48 WBS Boundaries" is created if absent (one row, project-scoped).

### 4.6 Satellite scene synthesis

For each of the 5 JPEGs, create a `SatelliteImage` row with:

- `sceneId`: `NH48-{YYYY}-{Q}` (e.g. `NH48-2025-Q1`)
- `acquisitionDate`: 1st of mid-quarter month (Feb 1 / May 1 / Aug 1 / Nov 1 / Feb 1)
- `bbox`: union envelope of all 7 WBS polygons
- `cloudCoverPercent`: 0–15%, deterministic from sceneId hash
- `storageUrl`: relative path produced by storing into the satellite storage root
- `mimeType`: `image/jpeg`

For each scene + each L2 WBS polygon, also insert a `ConstructionProgressSnapshot` with:

- `progressPercent`: linear interpolation between 0% (Q1 2025) and the activity's actual `percentComplete` as of latest EVM snapshot (Apr 2026)
- `analysisMethod`: `MANUAL_OVERLAY` (i.e. no NDVI inference — these are not real GeoTIFFs)
- `confidence`: 0.85 fixed

Bypass the `GeoTiffProcessor` entirely; the JPEGs aren't georeferenced and the seeder writes the row directly via the repository.

## 5. Idempotency & ordering

- Sentinel: `documentRepository.countByProjectId(nh48ProjectId) == 0`. Whole seeder no-ops if any document already exists for the project.
- Order: 150, runs after `NhaiRoadProjectSeeder` (140) and after `DefaultFolderStartupBackfill` so the project's default folders exist.
- Dev profile only (`@Profile("dev")`).

## 6. Testing

Two integration tests under `backend/bipros-api/src/test/java/com/bipros/api/config/seeder/`:

1. `NhaiRoadAttachmentsSeederTest` — boots Spring, runs seeder, asserts:
   - 29 document rows for the project (28 from register + 1 DOCX)
   - 5 of them have non-stub binaries (size > 4 KB)
   - At least 8 contract attachments distributed across the 4 `AttachmentEntityType`s (CONTRACT / MILESTONE / VARIATION_ORDER / PERFORMANCE_BOND)
   - 7 WBS polygons (one per L2 node) plus 1 GIS layer row
   - 5 satellite images
   - 35 construction progress snapshots (5 scenes × 7 polygons)

2. `MinimalPdfGeneratorTest` — generated PDF starts with `%PDF-1.` header, ends with `%%EOF`, opens cleanly via `org.apache.pdfbox.pdmodel.PDDocument.load(...)` (test-scoped PDFBox dependency only — production code stays dep-free).

## 7. Risks and trade-offs

- **Hand-rolled PDFs**: minor maintenance burden if the layout has to change; mitigated by the test that verifies the byte-for-byte output stays a valid PDF.
- **Synthetic GIS coordinates**: not real survey data. Tagged in the description field so downstream users know it's demo data.
- **`@Order` collisions**: this seeder claims 150. The IoclPanipatSeeder occupies 130 and the main NHAI seeder 140; 150 is free.
- **File size**: 5 PDFs (~80 KB) + 5 JPEGs (~480 KB) = ~560 KB added to the seeded jar. Acceptable.

## 8. Acceptance criteria

After a clean backend boot in dev profile:

1. `GET /api/documents?projectId=...` returns 29 documents for `BIPROS/NHAI/RJ/2025/001`.
2. Documents tab on the frontend shows 5 standard folders with 30 documents distributed.
3. `GET /api/contracts/{contractId}/attachments` returns ≥ 4 attachments for the EPC contract.
4. `GET /api/gis/wbs-polygons?projectId=...` returns 7 polygons drawn along the NH-48 corridor.
5. `GET /api/gis/satellite-images?projectId=...` returns 5 scenes spanning 2025-Q1 → 2026-Q1.
6. `GET /api/gis/construction-progress?projectId=...` returns a non-empty timeline.
7. Existing tabs (Activities, EVM, Cost, Risks, Daily Cost Report) remain unchanged.

## 9. Open follow-ups (intentionally deferred)

- Convert the JPEGs to real GeoTIFFs once ground-control-points are available, then run them through `GeoTiffProcessor` so NDVI/progress inference becomes meaningful.
- If the document register grows beyond 30 entries, consider sourcing it from a CSV in `seed-data/road-project/` rather than re-reading the xlsx sheet.
- A `prepare-seed-data.sh` to copy the source files into the classpath folder, executed once when the developer pulls fresh sample data.
