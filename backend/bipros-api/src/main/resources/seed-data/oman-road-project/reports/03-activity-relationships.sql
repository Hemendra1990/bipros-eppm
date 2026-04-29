-- BNK reports/03-activity-relationships.sql — Cross-section SS/FF logic for 6155.
--
-- Agent 2's seeder creates intra-section FINISH_TO_START chains (1.x→1.y,
-- 2.x→2.y, …) so each WBS section has its own internal FS network.  This
-- file adds the cross-section SS/FF links the workbooks imply but Java
-- doesn't generate:
--   * Earthworks SS Demolition (start when site cleared)
--   * Pavement SS Earthworks (start while final layers still being placed)
--   * Bridges SS Earthworks (parallel work on Wadi crossings)
--   * Drainage FF Pavement (drainage finishes with pavement)
--   * Road Furniture FF Pavement (signage finishes with pavement)
--   * Plantation SS Drainage (median works after drains in)
--
-- Code pattern: ACT-<boqItemNo>.  We pin to the first activity of each
-- section (lowest sort_order whose code starts with the section prefix).

INSERT INTO activity.activity_relationships (
    id, created_at, updated_at, version,
    project_id, predecessor_activity_id, successor_activity_id,
    relationship_type, lag, is_external
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       pred.project_id, pred.id, succ.id,
       rel.rel_type, rel.lag, FALSE
FROM (VALUES
    ('2', '3', 'START_TO_START',  5.0),    -- Earthworks 5d after Demolition starts
    ('3', '4', 'START_TO_START', 14.0),    -- Pavement 14d after Earthworks starts
    ('3', '5', 'START_TO_START',  7.0),    -- Bridges parallel to Earthworks
    ('6', '4', 'FINISH_TO_FINISH', 0.0),   -- Drainage finishes with Pavement
    ('7', '4', 'FINISH_TO_FINISH', 3.0),   -- Road Furniture 3d after Pavement
    ('8', '4', 'FINISH_TO_FINISH', 0.0),   -- Electrical finishes with Pavement
    ('9', '6', 'START_TO_START',  10.0)    -- Plantation 10d after Drainage starts
) AS rel(pred_section, succ_section, rel_type, lag)
JOIN LATERAL (
    SELECT a.id, a.project_id
    FROM   activity.activities a
    WHERE  a.project_id = (SELECT id FROM project.projects WHERE code = '6155')
      AND  a.code LIKE 'ACT-' || rel.pred_section || '.%'
    ORDER  BY a.sort_order NULLS LAST, a.code
    LIMIT  1
) pred ON TRUE
JOIN LATERAL (
    SELECT a.id
    FROM   activity.activities a
    WHERE  a.project_id = pred.project_id
      AND  a.code LIKE 'ACT-' || rel.succ_section || '.%'
    ORDER  BY a.sort_order NULLS LAST, a.code
    LIMIT  1
) succ ON TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM activity.activity_relationships r
    WHERE r.predecessor_activity_id = pred.id
      AND r.successor_activity_id   = succ.id
);

-- Mark critical-path activities (the pavement chain — section 4 — drives
-- the longest path through the schedule).
UPDATE activity.activities SET
    is_critical = TRUE,
    total_float = 0.0,
    free_float  = 0.0,
    updated_at  = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = '6155')
  AND code LIKE 'ACT-4.%';

-- Non-critical sections get small positive float (DCMA-14: 0 ≤ TF ≤ 44).
UPDATE activity.activities SET
    total_float = CASE
        WHEN code LIKE 'ACT-1.%' THEN 0.0   -- Preliminaries on critical front
        WHEN code LIKE 'ACT-2.%' THEN 5.0
        WHEN code LIKE 'ACT-3.%' THEN 0.0   -- Earthworks critical
        WHEN code LIKE 'ACT-5.%' THEN 12.0
        WHEN code LIKE 'ACT-6.%' THEN 8.0
        WHEN code LIKE 'ACT-7.%' THEN 18.0
        WHEN code LIKE 'ACT-8.%' THEN 22.0
        WHEN code LIKE 'ACT-9.%' THEN 30.0
        ELSE 6.0
    END,
    free_float = CASE
        WHEN code LIKE 'ACT-1.%' THEN 0.0
        WHEN code LIKE 'ACT-2.%' THEN 0.0
        WHEN code LIKE 'ACT-3.%' THEN 0.0
        WHEN code LIKE 'ACT-5.%' THEN 0.0
        WHEN code LIKE 'ACT-6.%' THEN 4.0
        WHEN code LIKE 'ACT-7.%' THEN 6.0
        WHEN code LIKE 'ACT-8.%' THEN 8.0
        WHEN code LIKE 'ACT-9.%' THEN 12.0
        ELSE 0.0
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = '6155')
  AND code NOT LIKE 'ACT-4.%';
