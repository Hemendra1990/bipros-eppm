-- 02-activity-relationships.sql
-- The OdishaSh10ProjectSeeder Java code already creates the bulk of the
-- intra-group + inter-group FS network at boot.  This file only adds a small
-- set of additional cross-package critical-path links (idempotent via NOT
-- EXISTS — the unique constraint name varies across migrations) and then
-- marks the project's true critical chain via UPDATE statements.

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
    -- Earthwork → Sub-base → Bituminous critical chain
    ('ACT-1.3', 'ACT-2.1'),
    ('ACT-2.2', 'ACT-2.3'),
    ('ACT-2.3', 'ACT-3.1'),
    ('ACT-3.1', 'ACT-3.2'),
    ('ACT-3.4', 'ACT-3.1'),
    -- Bituminous → Road furniture
    ('ACT-3.2', 'ACT-5.1'),
    ('ACT-5.1', 'ACT-5.4'),
    -- Structures cascade (substructure → superstructure for both bridge + ROB)
    ('ACT-6.1', 'ACT-6.2'),
    ('ACT-6.3', 'ACT-6.4'),
    -- Drainage runs in parallel with sub-base but feeds bituminous start
    ('ACT-4.1', 'ACT-3.1'),
    -- Project handover predecessors
    ('ACT-5.4', 'ACT-7.4'),
    ('ACT-7.4', 'ACT-7.5')
) AS rel(pred, succ)
JOIN activity.activities p ON p.code = rel.pred
 AND p.project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
JOIN activity.activities s ON s.code = rel.succ
 AND s.project_id = p.project_id
WHERE NOT EXISTS (
    SELECT 1 FROM activity.activity_relationships r
    WHERE r.predecessor_activity_id = p.id
      AND r.successor_activity_id   = s.id
);

-- Critical path: Earthwork → Sub-base → Bituminous → Road furniture → Handover.
-- Bridge / ROB substructure & superstructure are also on the critical chain
-- (ROB is the long-pole structure for the Cuttack approach).
UPDATE activity.activities SET
    is_critical = TRUE,
    total_float = 0.0,
    free_float = 0.0,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-1.1','ACT-1.2','ACT-1.3',
               'ACT-2.1','ACT-2.2','ACT-2.3',
               'ACT-3.1','ACT-3.2','ACT-3.4',
               'ACT-5.1','ACT-5.4',
               'ACT-6.3','ACT-6.4',
               'ACT-7.4','ACT-7.5');

-- Non-critical activities get positive float (DCMA: 0 ≤ TF ≤ 44 days).
UPDATE activity.activities SET
    total_float = CASE code
        WHEN 'ACT-3.3' THEN 6.0
        WHEN 'ACT-4.1' THEN 8.0
        WHEN 'ACT-4.2' THEN 12.0
        WHEN 'ACT-4.3' THEN 14.0
        WHEN 'ACT-4.4' THEN 10.0
        WHEN 'ACT-5.2' THEN 22.0
        WHEN 'ACT-5.3' THEN 18.0
        WHEN 'ACT-6.1' THEN 24.0
        WHEN 'ACT-6.2' THEN 24.0
        WHEN 'ACT-7.1' THEN 30.0
        WHEN 'ACT-7.2' THEN 32.0
        WHEN 'ACT-7.3' THEN 0.0
    END,
    free_float = CASE code
        WHEN 'ACT-3.3' THEN 0.0
        WHEN 'ACT-4.1' THEN 0.0
        WHEN 'ACT-4.2' THEN 12.0
        WHEN 'ACT-4.3' THEN 14.0
        WHEN 'ACT-4.4' THEN 10.0
        WHEN 'ACT-5.2' THEN 22.0
        WHEN 'ACT-5.3' THEN 18.0
        WHEN 'ACT-6.1' THEN 0.0
        WHEN 'ACT-6.2' THEN 24.0
        WHEN 'ACT-7.1' THEN 30.0
        WHEN 'ACT-7.2' THEN 32.0
        WHEN 'ACT-7.3' THEN 0.0
    END,
    is_critical = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-3.3','ACT-4.1','ACT-4.2','ACT-4.3','ACT-4.4',
               'ACT-5.2','ACT-5.3','ACT-6.1','ACT-6.2',
               'ACT-7.1','ACT-7.2','ACT-7.3');
