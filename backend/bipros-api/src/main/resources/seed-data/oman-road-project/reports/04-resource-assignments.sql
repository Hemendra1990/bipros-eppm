-- BNK reports/04-resource-assignments.sql — ResourceAssignment rows for 6155.
--
-- For every active activity (status NOT_STARTED, IN_PROGRESS, or COMPLETED),
-- create one ResourceAssignment per equipment resource that exists in the
-- 'resource' schema and was seeded by Agent 2.  We use a deterministic round-
-- robin so the assignments are stable across reruns.
--
-- Operability ask (plan §"Operability"): quantityRequired (planned_units)
-- filled in full; actual_units left at 60–80% of planned so the user can
-- demo "re-allocate resource" in the UI.
--
-- The unique constraint uk_assignment_activity_resource (activity_id,
-- resource_id) makes ON CONFLICT DO NOTHING safe.
--
-- Uses MOD over a stable ordering of equipment to pair each activity with
-- a single equipment resource (one assignment per activity).

WITH proj AS (
    SELECT id FROM project.projects WHERE code = '6155'
),
acts AS (
    SELECT a.id   AS activity_id,
           a.project_id,
           a.planned_start_date, a.planned_finish_date,
           a.actual_start_date,  a.actual_finish_date,
           a.percent_complete,
           ROW_NUMBER() OVER (ORDER BY COALESCE(a.sort_order, 0), a.code) - 1 AS act_idx
    FROM   activity.activities a, proj
    WHERE  a.project_id = proj.id
      AND  a.status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')
),
eqp AS (
    SELECT r.id, r.code,
           ROW_NUMBER() OVER (ORDER BY r.code) - 1 AS res_idx,
           COUNT(*) OVER ()                        AS res_count
    FROM   resource.resources r
    WHERE  r.code LIKE 'BNK-EQ-%'
),
pairs AS (
    SELECT a.activity_id,
           a.project_id,
           a.planned_start_date, a.planned_finish_date,
           a.actual_start_date,  a.actual_finish_date,
           a.percent_complete,
           e.id AS resource_id
    FROM   acts a
    JOIN   eqp  e ON e.res_idx = MOD(a.act_idx, GREATEST(e.res_count, 1))
)
INSERT INTO resource.resource_assignments (
    id, created_at, updated_at, version,
    activity_id, resource_id, project_id,
    planned_units, actual_units, remaining_units, at_completion_units,
    planned_cost,  actual_cost,  remaining_cost,  at_completion_cost,
    rate_type,
    planned_start_date, planned_finish_date,
    actual_start_date,  actual_finish_date
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.activity_id, p.resource_id, p.project_id,
       100.0,                                          -- quantityRequired
       60.0 + MOD(ABS(HASHTEXT(p.activity_id::text)), 21)::numeric,  -- 60..80%
       100.0 - (60.0 + MOD(ABS(HASHTEXT(p.activity_id::text)), 21)::numeric),
       100.0,
       50000.000, 30000.000, 20000.000, 50000.000,    -- placeholder OMR amounts
       'BUDGETED',
       p.planned_start_date, p.planned_finish_date,
       p.actual_start_date,  p.actual_finish_date
FROM   pairs p
WHERE  NOT EXISTS (
    SELECT 1 FROM resource.resource_assignments ra
    WHERE  ra.activity_id = p.activity_id
      AND  ra.resource_id = p.resource_id
);
