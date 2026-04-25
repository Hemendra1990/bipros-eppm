-- 02-activity-relationships.sql
-- Build an FS network and mark the critical path.  This resolves the
-- DCMA-14 "Missing logic" failure (currently 15) and "No critical path
-- identified".  Pavement chain (1→2→3→5→7) is critical; bridges and
-- drainage run in parallel with positive float.

-- Idempotent — the NhaiRoadProjectSeeder Java code already creates a base set of
-- intra-group + inter-group FS relationships, so we skip any pair that already exists
-- via NOT EXISTS rather than relying on ON CONFLICT (the unique constraint name varies
-- across migrations).  This keeps the bundle re-runnable and lets boots that hit a
-- partially-seeded DB succeed.
INSERT INTO activity.activity_relationships (
    id, created_at, updated_at, version,
    project_id, predecessor_activity_id, successor_activity_id,
    relationship_type, lag, is_external
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.project_id, p.id, s.id,
    'FINISH_TO_START', 0.0, FALSE
FROM (VALUES
    ('ACT-1.1', 'ACT-1.2'),
    ('ACT-1.2', 'ACT-2.1'),
    ('ACT-2.1', 'ACT-2.2'),
    ('ACT-2.2', 'ACT-3.1'),
    ('ACT-3.1', 'ACT-3.2'),
    ('ACT-3.2', 'ACT-5.1'),
    ('ACT-5.1', 'ACT-5.2'),
    ('ACT-5.2', 'ACT-5.3'),
    ('ACT-5.3', 'ACT-7.1'),
    ('ACT-6.1', 'ACT-6.2'),
    ('ACT-1.1', 'ACT-4.1'),
    ('ACT-1.2', 'ACT-4.2'),
    ('ACT-4.2', 'ACT-4.3')
) AS rel(pred, succ)
JOIN activity.activities p ON p.code = rel.pred
 AND p.project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001')
JOIN activity.activities s ON s.code = rel.succ
 AND s.project_id = p.project_id
WHERE NOT EXISTS (
    SELECT 1 FROM activity.activity_relationships r
    WHERE r.predecessor_activity_id = p.id
      AND r.successor_activity_id   = s.id
);

-- Critical path: the pavement chain from Earthwork → Misc.
UPDATE activity.activities SET
    is_critical = TRUE,
    total_float = 0.0,
    free_float = 0.0,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001')
  AND code IN ('ACT-1.1','ACT-1.2','ACT-2.1','ACT-2.2','ACT-3.1','ACT-3.2',
               'ACT-5.1','ACT-5.2','ACT-5.3','ACT-7.1');

-- Non-critical activities get positive float (DCMA: 0 ≤ TF ≤ 44 days).
UPDATE activity.activities SET
    total_float = CASE code
        WHEN 'ACT-4.1' THEN 14.0
        WHEN 'ACT-4.2' THEN 9.0
        WHEN 'ACT-4.3' THEN 6.0
        WHEN 'ACT-6.1' THEN 18.0
        WHEN 'ACT-6.2' THEN 18.0
    END,
    free_float = CASE code
        WHEN 'ACT-4.1' THEN 0.0
        WHEN 'ACT-4.2' THEN 3.0
        WHEN 'ACT-4.3' THEN 6.0
        WHEN 'ACT-6.1' THEN 0.0
        WHEN 'ACT-6.2' THEN 18.0
    END,
    is_critical = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001')
  AND code IN ('ACT-4.1','ACT-4.2','ACT-4.3','ACT-6.1','ACT-6.2');
