-- BNK reports/22-activity-codes.sql — Bind the three activity-code categories
-- (DISC, PHASE, ZONE) to every Oman activity so the Activity Codes panel is populated.
--
-- The three category rows already exist in activity.activity_codes (seeded by
-- IcpmsActivityCodeSeeder). For the Oman project we just create assignments —
-- one row per (activity, category) — with a code_value derived from each
-- activity's WBS chapter, status, or natural code.
--
-- Mapping logic:
--   DISC (Discipline)  ← WBS chapter:    1→MISC, 2→EARTH, 3→DRAIN, 4→DRAIN, 5→PAVE,
--                                       7→ELEC, 8→BRIDGE, 9→FINISH, ROOT→MISC
--   PHASE (Phase)      ← Activity status: NOT_STARTED→P3-FINISH, IN_PROGRESS→P2-EXEC,
--                                          COMPLETED→P1-PRELIM
--   ZONE (Zone)        ← deterministic on activity code: every 3rd → Z1-BARKA,
--                                          Z2-MID, Z3-NAKHAL  (modulo on octets of activity_id)
--
-- Idempotency: ON CONFLICT (activity_id, activity_code_id) DO NOTHING via the table's
-- unique constraint.

-- ─────────────────────────── Discipline ───────────────────────────
INSERT INTO activity.activity_code_assignments (
    id, created_at, updated_at, version,
    activity_id, activity_code_id, code_value
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       a.id,
       (SELECT id FROM activity.activity_codes WHERE name LIKE 'DISC%' LIMIT 1),
       CASE
           WHEN w.code LIKE 'WBS-1%'    THEN 'MISC'
           WHEN w.code LIKE 'WBS-2%'    THEN 'EARTH'
           WHEN w.code LIKE 'WBS-3%'    THEN 'DRAIN'
           WHEN w.code LIKE 'WBS-4%'    THEN 'DRAIN'
           WHEN w.code LIKE 'WBS-5%'    THEN 'PAVE'
           WHEN w.code LIKE 'WBS-7%'    THEN 'ELEC'
           WHEN w.code LIKE 'WBS-8%'    THEN 'BRIDGE'
           WHEN w.code LIKE 'WBS-9%'    THEN 'FINISH'
           ELSE                              'MISC'
       END
FROM activity.activities a
JOIN project.wbs_nodes w ON w.id = a.wbs_node_id
WHERE a.project_id = (SELECT id FROM project.projects WHERE code = '6155')
ON CONFLICT (activity_id, activity_code_id) DO NOTHING;

-- ─────────────────────────── Phase ───────────────────────────
INSERT INTO activity.activity_code_assignments (
    id, created_at, updated_at, version,
    activity_id, activity_code_id, code_value
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       a.id,
       (SELECT id FROM activity.activity_codes WHERE name LIKE 'PHASE%' LIMIT 1),
       CASE a.status
           WHEN 'COMPLETED'   THEN 'P1-PRELIM'
           WHEN 'IN_PROGRESS' THEN 'P2-EXEC'
           ELSE                    'P3-FINISH'
       END
FROM activity.activities a
WHERE a.project_id = (SELECT id FROM project.projects WHERE code = '6155')
ON CONFLICT (activity_id, activity_code_id) DO NOTHING;

-- ─────────────────────────── Zone ───────────────────────────
-- Deterministic: hash first 8 hex chars of activity.id mod 3.
INSERT INTO activity.activity_code_assignments (
    id, created_at, updated_at, version,
    activity_id, activity_code_id, code_value
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       a.id,
       (SELECT id FROM activity.activity_codes WHERE name LIKE 'ZONE%' LIMIT 1),
       CASE (('x' || SUBSTRING(REPLACE(a.id::text,'-',''), 1, 8))::bit(32)::int) % 3
           WHEN 0 THEN 'Z1-BARKA'
           WHEN 1 THEN 'Z2-MID'
           ELSE        'Z3-NAKHAL'
       END
FROM activity.activities a
WHERE a.project_id = (SELECT id FROM project.projects WHERE code = '6155')
ON CONFLICT (activity_id, activity_code_id) DO NOTHING;
