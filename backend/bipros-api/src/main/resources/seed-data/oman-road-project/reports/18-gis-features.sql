-- BNK reports/18-gis-features.sql — GIS features for Oman Barka-Nakhal Road project (code 6155)
-- Seeds: 2 GisLayer rows, 9 WbsPolygon rows (one per L2 WBS package), 5 SatelliteImage rows, 5 ConstructionProgressSnapshot rows.
-- Coordinates: Barka 23.69°N 57.89°E (north end) → Nakhal 23.39°N 57.83°E (south end). 9 polygons distributed along the corridor.

-- ─── 1. GIS LAYERS (2 rows) ────────────────────────────────────────────────
INSERT INTO gis.gis_layers (id, created_at, updated_at, layer_name, layer_type, description, is_visible, opacity, sort_order, project_id)
SELECT gen_random_uuid(), now(), now(),
       'WBS Packages', 'WBS_POLYGON',
       'L2 WBS package boundaries along the Barka-Nakhal corridor', true, 0.7, 1,
       p.id
FROM project.projects p
WHERE p.code = '6155'
  AND NOT EXISTS (SELECT 1 FROM gis.gis_layers l WHERE l.project_id = p.id AND l.layer_name = 'WBS Packages');

INSERT INTO gis.gis_layers (id, created_at, updated_at, layer_name, layer_type, description, is_visible, opacity, sort_order, project_id)
SELECT gen_random_uuid(), now(), now(),
       'Satellite Imagery', 'SATELLITE_OVERLAY',
       'Quarterly satellite captures of the Barka-Nakhal corridor', true, 1.0, 0,
       p.id
FROM project.projects p
WHERE p.code = '6155'
  AND NOT EXISTS (SELECT 1 FROM gis.gis_layers l WHERE l.project_id = p.id AND l.layer_name = 'Satellite Imagery');

-- ─── 2. WBS POLYGONS (9 rows, one per L2 WBS package) ────────────────────
-- Each polygon is a small ~0.02° (≈2km) box, distributed along the corridor by sort_order.
WITH p AS (SELECT id FROM project.projects WHERE code = '6155'),
     layer AS (SELECT id FROM gis.gis_layers WHERE layer_name = 'WBS Packages' AND project_id = (SELECT id FROM p)),
     wbs AS (
       SELECT w.id AS wbs_node_id, w.code, w.name,
              ROW_NUMBER() OVER (ORDER BY w.code) AS rn
       FROM project.wbs_nodes w
       WHERE w.project_id = (SELECT id FROM p)
         AND w.parent_id IS NOT NULL
         AND w.code LIKE 'WBS-_'  -- L2 only (e.g. WBS-1..WBS-9)
       ORDER BY w.code
       LIMIT 9
     )
INSERT INTO gis.wbs_polygons (id, created_at, updated_at,
                              project_id, layer_id, wbs_node_id, wbs_code, wbs_name,
                              center_latitude, center_longitude, polygon,
                              fill_color, stroke_color, area_in_sq_meters)
SELECT gen_random_uuid(), now(), now(),
       (SELECT id FROM p),
       (SELECT id FROM layer),
       wbs.wbs_node_id, wbs.code, wbs.name,
       -- north→south distribution along corridor: 23.69 (Barka) − (rn-1) × 0.0375 → ~23.39 (Nakhal)
       (23.69 - (wbs.rn - 1) * 0.0375)::double precision AS center_lat,
       (57.89 - (wbs.rn - 1) * 0.0075)::double precision AS center_lng,
       ST_GeomFromText(
         'POLYGON((' ||
         (57.89 - (wbs.rn - 1) * 0.0075 - 0.01) || ' ' || (23.69 - (wbs.rn - 1) * 0.0375 - 0.01) || ',' ||
         (57.89 - (wbs.rn - 1) * 0.0075 + 0.01) || ' ' || (23.69 - (wbs.rn - 1) * 0.0375 - 0.01) || ',' ||
         (57.89 - (wbs.rn - 1) * 0.0075 + 0.01) || ' ' || (23.69 - (wbs.rn - 1) * 0.0375 + 0.01) || ',' ||
         (57.89 - (wbs.rn - 1) * 0.0075 - 0.01) || ' ' || (23.69 - (wbs.rn - 1) * 0.0375 + 0.01) || ',' ||
         (57.89 - (wbs.rn - 1) * 0.0075 - 0.01) || ' ' || (23.69 - (wbs.rn - 1) * 0.0375 - 0.01) ||
         '))',
         4326
       ) AS polygon,
       -- fill / stroke cycle
       (ARRAY['#FFB800','#0078D4','#107C10','#5C2D91','#E3008C','#00B7C3','#FF8C00','#8E562E','#737373'])[wbs.rn] AS fill_color,
       (ARRAY['#A06800','#003C66','#0E5A0E','#3D1F60','#9A0060','#006A75','#A65A00','#5C381E','#404040'])[wbs.rn] AS stroke_color,
       4_000_000.0 AS area_in_sq_meters  -- ~2km × 2km
FROM wbs
WHERE NOT EXISTS (
  SELECT 1 FROM gis.wbs_polygons existing
  WHERE existing.project_id = (SELECT id FROM p) AND existing.wbs_node_id = wbs.wbs_node_id
);

-- ─── 3. SATELLITE IMAGES (5 rows, quarterly captures) ────────────────────
WITH p AS (SELECT id FROM project.projects WHERE code = '6155'),
     layer AS (SELECT id FROM gis.gis_layers WHERE layer_name = 'Satellite Imagery' AND project_id = (SELECT id FROM p))
INSERT INTO gis.satellite_images (id, created_at, updated_at,
                                  project_id, layer_id, image_name, capture_date, source, status,
                                  file_path, file_size, mime_type, scene_id,
                                  cloud_cover_percent, resolution,
                                  north_bound, south_bound, east_bound, west_bound,
                                  description)
SELECT gen_random_uuid(), now(), now(),
       (SELECT id FROM p),
       (SELECT id FROM layer),
       'Barka-Nakhal Q' || q.qnum AS image_name,
       q.capture_date,
       'SENTINEL_HUB', 'READY',
       'classpath:seed-data/oman-road-project/satellite/barka-nakhal-q' || q.qnum || '.jpeg',
       644,
       'image/jpeg',
       'BNK-Q' || q.qnum || '-2025',
       q.cloud,
       '10m/pixel',
       23.70, 23.38, 57.91, 57.81,
       'Quarterly Sentinel-2 capture of the Barka-Nakhal corridor — Q' || q.qnum
FROM (VALUES
  (1, DATE '2024-12-15', 5.0),
  (2, DATE '2025-03-15', 8.0),
  (3, DATE '2025-06-15', 2.0),
  (4, DATE '2025-09-15', 12.0),
  (5, DATE '2026-01-15', 4.0)
) AS q(qnum, capture_date, cloud)
WHERE NOT EXISTS (
  SELECT 1 FROM gis.satellite_images si
  WHERE si.scene_id = 'BNK-Q' || q.qnum || '-2025'
);

-- ─── 4. CONSTRUCTION PROGRESS SNAPSHOTS (5 rows, quarterly) ──────────────
-- One snapshot per quarterly satellite image, at the project level (no specific WBS package).
INSERT INTO gis.construction_progress_snapshots (id, created_at, updated_at,
                                                 project_id, capture_date,
                                                 analysis_method, analyzer_id,
                                                 ai_progress_percent, contractor_claimed_percent,
                                                 derived_progress_percent, variance_percent,
                                                 ndvi_change, edi, cvi, alert_flag,
                                                 wbs_package_code, satellite_image_id,
                                                 analysis_duration_ms, analysis_cost_micros,
                                                 remarks)
SELECT gen_random_uuid(), now(), now(),
       (SELECT id FROM project.projects WHERE code = '6155'),
       q.capture_date,
       'AI_SEGMENTATION', 'sentinel-pipeline-v3',
       q.ai_progress, q.claimed, q.derived, q.derived - q.claimed,
       q.ndvi, q.edi, q.cvi, q.alert,
       q.wbs_pkg,
       (SELECT si.id FROM gis.satellite_images si
        WHERE si.project_id = (SELECT id FROM project.projects WHERE code = '6155')
          AND si.scene_id = 'BNK-Q' || q.qnum || '-2025'
        LIMIT 1),
       2400, 250000,
       q.remarks
FROM (VALUES
  (1, DATE '2024-12-15',  4.0,  5.0,  4.5,  -0.04, 0.62, 0.48, 'GREEN',              'WBS-1', 'Q1 mobilization snapshot — site clearing complete'),
  (2, DATE '2025-03-15', 22.0, 24.0, 21.5,  -0.18, 0.58, 0.55, 'GREEN',              'WBS-3', 'Q2 earthworks active across stretches 1 & 2'),
  (3, DATE '2025-06-15', 41.0, 45.0, 42.0,  -0.30, 0.51, 0.62, 'AMBER_VARIANCE_GT5', 'WBS-3', 'Q3 earthworks ahead of plan; bridge piers Pier P2/P3 visible'),
  (4, DATE '2025-09-15', 58.0, 62.0, 57.5,  -0.42, 0.44, 0.71, 'AMBER_VARIANCE_GT5', 'WBS-4', 'Q4 GSB layer placed across all 4 stretches'),
  (5, DATE '2026-01-15', 72.0, 75.0, 71.0,  -0.48, 0.39, 0.78, 'GREEN',              'WBS-4', 'FY26 Q1 DBM in progress; pavement BC layer pending')
) AS q(qnum, capture_date, ai_progress, claimed, derived, ndvi, edi, cvi, alert, wbs_pkg, remarks)
WHERE EXISTS (SELECT 1 FROM project.projects WHERE code = '6155')
  AND NOT EXISTS (
    SELECT 1 FROM gis.construction_progress_snapshots cps
    WHERE cps.project_id = (SELECT id FROM project.projects WHERE code = '6155')
      AND cps.capture_date = q.capture_date
      AND cps.analysis_method = 'AI_SEGMENTATION'
  );
