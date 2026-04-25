---
title: Odisha SH-10 Seeder + NH-48 Attachments + Risk DOCX Integration
date: 2026-04-25
project: bipros-eppm
audience: backend / data-seeder maintainers
status: approved-for-implementation
supersedes: 2026-04-25-road-seeder-attachments-design.md (subsumes & extends)
---

# Odisha SH-10 Seeder + NH-48 Attachments + Risk DOCX Integration

## 1. Goal

Round out the road-construction demo so every project tab is populated with realistic, calculated data, and add a second parallel road project for state-government contrast against the existing NHAI NH-48 demo.

Three deliverables in one feature:

1. **NH-48 attachments seeder** — implements the existing 2026-04-25 design doc (documents, contract attachments, GIS polygons, satellite imagery, construction-progress snapshots).
2. **Odisha SH-10 project seeder** — a brand-new programmatic seeder for an OWD road-widening project at ~75% completion, with full WBS / activities / EVM / cost / risks / contracts / GIS / attachments coverage.
3. **Risk Master DOCX integration** — enriches the global Risk Library from the user-supplied 11-category, 33-risk framework, and uploads the DOCX itself as a project document on both projects.

## 2. Scope

In scope:
- 4 new Java classes: `NhaiRoadAttachmentsSeeder`, `OdishaSh10ProjectSeeder`, `OdishaSh10AttachmentsSeeder`, `MinimalPdfGenerator` (shared util).
- 1 new enum value: `RiskCategory.HEALTH_SAFETY`.
- 25+ new entries in `RiskTemplateSeeder` from the Risk Master DOCX.
- 14 new SQL files under `seed-data/odisha-sh10/reports/` mirroring NH-48 reports bundle, with deeper history (75% complete demands ~21 EVM snapshots, 9 RA bills, 30 cash-flow forecasts).
- Source files (5 PDFs + 5 JPEGs + 1 DOCX) copied into both `seed-data/road-project/` and `seed-data/odisha-sh10/` resource folders.
- 12-row Odisha SH-10 risk register with 2-4 RiskResponse rows each (Avoid/Mitigate/Transfer/Accept strategies from the DOCX).

Out of scope:
- Modifying `NhaiRoadProjectSeeder` itself, its workbook reader, or its existing reports SQL.
- Production migrations (dev profile only — `ddl-auto: create-drop`).
- Refactoring NHAI seeder to share a base class with Odisha — they are intentionally independent narratives.

## 3. Architecture

### 3.1 New files

```
backend/bipros-api/src/main/java/com/bipros/api/config/seeder/
├── NhaiRoadAttachmentsSeeder.java          @Order(145), @Profile("dev")
├── OdishaSh10ProjectSeeder.java            @Order(160), @Profile("dev")
├── OdishaSh10AttachmentsSeeder.java        @Order(165), @Profile("dev")
└── util/
    ├── MinimalPdfGenerator.java             (shared — emits hand-rolled PDFs)
    └── CorridorGeometry.java                (shared — chainage→polygon helper)

backend/bipros-api/src/main/resources/seed-data/
├── road-project/                           (existing)
│   ├── documents/                          (NEW — 6 binaries)
│   ├── satellite/                          (NEW — 5 JPEGs)
│   └── reports/*.sql                        (existing — 14 files, untouched)
└── odisha-sh10/                            (NEW)
    ├── documents/                          (5 renamed PDFs + DOCX)
    ├── satellite/                          (5 renamed JPEGs)
    └── reports/                            (14 SQL files: 00-wbs through 13-tenders)

backend/bipros-risk/src/main/java/com/bipros/risk/domain/model/
└── RiskCategory.java                       (NEW enum value: HEALTH_SAFETY)

docs/superpowers/specs/
└── 2026-04-25-odisha-sh10-and-attachments-design.md   (this file)
```

### 3.2 Spring `@Order` map

| Order | Component | Status |
|---:|---|---|
| 60 | RiskTemplateSeeder | enriched (+25 from DOCX) |
| 140 | NhaiRoadProjectSeeder | unchanged |
| 145 | NhaiRoadAttachmentsSeeder | NEW |
| 160 | OdishaSh10ProjectSeeder | NEW |
| 165 | OdishaSh10AttachmentsSeeder | NEW |

## 4. Odisha SH-10 project parameters

| Field | Value |
|---|---|
| Code | `OWD/SH10/OD/2025/001` |
| Name | SH-10 Bhubaneswar–Cuttack 4-laning & Strengthening |
| Client | Odisha Works Department (OWD) |
| Implementing Agency | OWD PIU Bhubaneswar |
| Contractor | Megha Engineering & Infrastructures Ltd |
| Project Manager | Er. Subrat K. Mohanty |
| Contract value | ₹ 452.50 crores |
| Length | 28.000 km |
| Chainage | Km 0+000 → Km 28+000 |
| Planned start / finish | 2024-08-01 / 2026-12-31 |
| Data date | 2026-04-25 |
| % Complete | ~75 % weighted |
| Calendar | SH10-OWD-6day (Mon–Sat 9 hrs/day) |

7 WBS L2 packages totalling ₹452.5 cr (Earthwork ₹65 / Sub-base ₹85 / Bituminous ₹140 / Drainage ₹40 / Road furniture ₹25 / Structures ₹72.5 / Misc ₹25), with package-level percent-complete spanning 10 % (Misc) to 98 % (Earthwork).

EVM target as of 2026-04-25: BAC ₹452.5 cr, PV ₹360 cr, EV ₹339.4 cr, AC ₹358.2 cr → SPI 0.943, CPI 0.948, EAC ₹477.4 cr, VAC −₹24.9 cr.

Historical depth: ~120 DPRs, ~120 deployments, ~120 weather rows, 80 material logs, 9 monthly RA bills, 21 monthly EVM snapshots, 30 cash-flow forecasts, 4 funding tranches, 1 awarded tender, 12 risks (with mitigation responses), 3 variation orders.

## 5. Risk DOCX integration

### 5.1 Library enrichment (global)

`RiskTemplateSeeder` gains 25 new road-specific templates derived from DOCX sections 4.1–4.11. Codes follow the existing `ROAD-XXX-NN` shape with new prefixes: `ROAD-LA-12`, `ROAD-GEO-13`, `ROAD-MW-14`, `ROAD-MAT-15`, `ROAD-LAB-16`, `ROAD-ENV-17`, `ROAD-FIN-18`, `ROAD-DES-19`, `ROAD-HSE-20`, `ROAD-LO-21`, `ROAD-FM-22` (and sub-numbered for the 3 risks per category). The new `HEALTH_SAFETY` enum value captures HSE risks correctly.

### 5.2 Project register (Odisha)

12 active risks in `09-risks.sql` for Odisha, customised for OWD/Odisha context (Cyclone Dana, Mahanadi soft clay, OPTCL utility shifting, Chandaka WLS buffer, etc.). Each gets 2-4 `RiskResponse` rows with Avoid/Mitigate/Transfer/Accept/Contingency strategies from the DOCX, with realistic estimated/actual costs and PLANNED/IN_PROGRESS/COMPLETED status.

NH-48's existing 8 risks remain untouched.

### 5.3 DOCX as a project document

The DOCX is uploaded as a binary on both projects under the "Plans" or "Reference" folder so users can open the framework directly from the document register.

## 6. Attachments / GIS / Satellite (both projects)

For each project:
- **~25-30 documents** in the document register (real PDFs where they exist, generated stub PDFs for the rest, all linked into existing folders by category).
- **Contract attachments** at every level (contract-level, milestone-level, variation-order-level, performance-bond-level — 8+ rows distributed across `AttachmentEntityType`).
- **7 WBS polygons** per project, drawn along the project corridor (NH-48 in Rajasthan: 26.65°N to 26.78°N; SH-10 in Odisha: 20.30°N to 20.46°N). Synthetic but tagged as demo data.
- **5 satellite scenes** per project, spanning 2024-Q4/Q1-2025 → Q1-2026, with bbox = union of WBS polygons.
- **35 construction progress snapshots** per project (5 scenes × 7 polygons), with progressPercent linearly interpolated from 0 % at scene 1 to actual `percentComplete` at the latest scene.

Stub PDFs use a hand-rolled 8-object PDF skeleton (~600 bytes each, single-page A4 with project header + document metadata) — no new Maven dependency.

## 7. Idempotency & ordering

Every seeder no-ops if its sentinel hits:
- `OdishaSh10ProjectSeeder` — project lookup by code `OWD/SH10/OD/2025/001`.
- Both attachments seeders — count of documents for the project.
- `RiskTemplateSeeder` — already idempotent per `code`.

Safe to re-run on `ddl-auto: create-drop` (DB is empty at boot anyway).

## 8. Testing

- One smoke integration test per seeder (boot Spring, run, assert key row counts: 8 NH-48 risks + 12 Odisha risks, 7 polygons each, 5 scenes each, etc.). Tests live under `backend/bipros-api/src/test/java/com/bipros/api/config/seeder/`.
- Manual verification: clean boot dev profile, hit the relevant `/api/...` endpoints, walk the frontend tabs (Activities, EVM, Cost, Risks, Documents, GIS, Contracts) for both projects.

## 9. Risks & trade-offs

- Hand-rolled PDFs: minor maintenance burden; mitigated by a unit test asserting valid PDF header/trailer.
- Synthetic GIS coordinates: tagged in description so users know it's demo data.
- Two near-identical attachments seeders: deliberate duplication for readability over DRY; only true shared code is the helper utilities.
- The Odisha SH-10 register references activity codes (`ACT-1.1` etc.) that will be created by `OdishaSh10ProjectSeeder` — order 160 must come before order 165, and the SQL bundle inside 160 must run *after* activity insertion (mirrors NH-48 pattern).

## 10. Acceptance criteria

After a clean dev-profile boot:
1. `GET /api/projects` returns both projects (`BIPROS/NHAI/RJ/2025/001` + `OWD/SH10/OD/2025/001`).
2. NH-48 has ≥ 28 documents (with 5 real PDFs ≥ 4 KB each), 8 risks, 7 WBS polygons, 5 satellite scenes.
3. SH-10 has ≥ 25 documents, 12 risks (each with ≥ 2 RiskResponse rows), 28 BOQ items, 28 activities with FS relationships, 21 EVM snapshots, 9 RA bills, 7 WBS polygons, 5 satellite scenes.
4. Risk Library has ≥ 33 system-default templates (existing 8 + 25 from DOCX).
5. EVM endpoint returns SPI ≈ 0.943, CPI ≈ 0.948 for SH-10.
