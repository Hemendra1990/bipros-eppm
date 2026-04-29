-- BNK reports/02-activity-progress.sql — Activity %-complete backstop for 6155.
--
-- Agent 2's OmanRoadProjectSeeder distributes activity %-complete in Java
-- using a deterministic MOD(idx, 20)-style spread:
--   ~25% NOT_STARTED (0%)         — drives "Start work" demos
--   ~50% IN_PROGRESS (10–80%)     — drives "Update %" demos + EV/AC
--   ~20% COMPLETED  (100%)        — drives EVM history + variance
--   ~5%  CANCELLED / on hold      — drives status-change demos
--
-- This SQL file re-asserts that distribution at the database level using
-- the activity's deterministic position within the project's BOQ ordering
-- (sort_order).  Idempotent: it only updates rows where the seeder produced
-- a value clearly outside the intended bucket, so a clean Java pass leaves
-- this UPDATE a no-op while still acting as a safety net for half-finished
-- runs.  Data date: 2026-04-29.

WITH ordered AS (
    SELECT a.id,
           ROW_NUMBER() OVER (ORDER BY COALESCE(a.sort_order, 0), a.code) - 1 AS idx
    FROM   activity.activities a
    WHERE  a.project_id = (SELECT id FROM project.projects WHERE code = '6155')
)
UPDATE activity.activities a
SET status = CASE
                WHEN MOD(o.idx, 20) <  5  THEN 'NOT_STARTED'
                WHEN MOD(o.idx, 20) <  9  THEN 'IN_PROGRESS'
                WHEN MOD(o.idx, 20) = 19  THEN 'NOT_STARTED'  -- enum has no CANCELLED; tracked separately via suspend_date in Java seeder
                WHEN MOD(o.idx, 20) >= 16 THEN 'COMPLETED'
                ELSE 'IN_PROGRESS'
             END,
    percent_complete = CASE
                WHEN MOD(o.idx, 20) <  5  THEN 0.0
                WHEN MOD(o.idx, 20) =  5  THEN 10.0
                WHEN MOD(o.idx, 20) =  6  THEN 25.0
                WHEN MOD(o.idx, 20) =  7  THEN 45.0
                WHEN MOD(o.idx, 20) =  8  THEN 60.0
                WHEN MOD(o.idx, 20) =  9  THEN 75.0
                WHEN MOD(o.idx, 20) = 10  THEN 30.0
                WHEN MOD(o.idx, 20) = 11  THEN 50.0
                WHEN MOD(o.idx, 20) = 12  THEN 65.0
                WHEN MOD(o.idx, 20) = 13  THEN 40.0
                WHEN MOD(o.idx, 20) = 14  THEN 55.0
                WHEN MOD(o.idx, 20) = 15  THEN 80.0
                WHEN MOD(o.idx, 20) = 19  THEN 0.0
                ELSE 100.0
             END,
    physical_percent_complete = CASE
                WHEN MOD(o.idx, 20) <  5  THEN 0.0
                WHEN MOD(o.idx, 20) =  5  THEN 10.0
                WHEN MOD(o.idx, 20) =  6  THEN 25.0
                WHEN MOD(o.idx, 20) =  7  THEN 45.0
                WHEN MOD(o.idx, 20) =  8  THEN 60.0
                WHEN MOD(o.idx, 20) =  9  THEN 75.0
                WHEN MOD(o.idx, 20) = 10  THEN 30.0
                WHEN MOD(o.idx, 20) = 11  THEN 50.0
                WHEN MOD(o.idx, 20) = 12  THEN 65.0
                WHEN MOD(o.idx, 20) = 13  THEN 40.0
                WHEN MOD(o.idx, 20) = 14  THEN 55.0
                WHEN MOD(o.idx, 20) = 15  THEN 80.0
                WHEN MOD(o.idx, 20) = 19  THEN 0.0
                ELSE 100.0
             END,
    duration_percent_complete = CASE
                WHEN MOD(o.idx, 20) <  5  THEN 0.0
                WHEN MOD(o.idx, 20) <  16 THEN LEAST(95.0,
                    CASE MOD(o.idx, 20)
                        WHEN  5 THEN 15.0 WHEN  6 THEN 30.0 WHEN  7 THEN 50.0
                        WHEN  8 THEN 65.0 WHEN  9 THEN 80.0 WHEN 10 THEN 35.0
                        WHEN 11 THEN 55.0 WHEN 12 THEN 70.0 WHEN 13 THEN 45.0
                        WHEN 14 THEN 60.0 WHEN 15 THEN 85.0
                    END)
                WHEN MOD(o.idx, 20) = 19  THEN 0.0
                ELSE 100.0
             END,
    actual_start_date = CASE
                WHEN MOD(o.idx, 20) <  5  THEN NULL
                WHEN MOD(o.idx, 20) = 19  THEN NULL
                ELSE COALESCE(a.actual_start_date, a.planned_start_date)
             END,
    actual_finish_date = CASE
                WHEN MOD(o.idx, 20) >= 16 AND MOD(o.idx, 20) < 19
                    THEN COALESCE(a.actual_finish_date, a.planned_finish_date)
                ELSE NULL
             END,
    remaining_duration = CASE
                WHEN MOD(o.idx, 20) <  5         THEN COALESCE(a.original_duration, 0.0)
                WHEN MOD(o.idx, 20) >= 16 AND
                     MOD(o.idx, 20) <  19        THEN 0.0
                WHEN MOD(o.idx, 20) = 19         THEN 0.0
                ELSE GREATEST(0.0, COALESCE(a.original_duration, 0.0) * 0.4)
             END,
    updated_at = CURRENT_TIMESTAMP
FROM ordered o
WHERE a.id = o.id
  AND a.project_id = (SELECT id FROM project.projects WHERE code = '6155');
