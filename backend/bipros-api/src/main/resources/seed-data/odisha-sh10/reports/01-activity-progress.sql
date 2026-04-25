-- 01-activity-progress.sql
-- Drive each ACT-x.y to the percent-complete implied by its WBS package
-- target (data date 2026-04-25).  WBS-1 (Earthwork) and WBS-2 (Sub-base) are
-- effectively complete; WBS-3 (Bituminous) is mid-execution at ~72%; WBS-4
-- (Drainage) at 80%; WBS-5 (Road Furniture) just started at 30%; WBS-6
-- (Structures) at 65%; WBS-7 (Misc / utility shifting / handover) mostly
-- not started, with site-office setup (7.3) flagged complete.

-- ──────────── COMPLETED activities (WBS-1 + WBS-2) ──────────────
UPDATE activity.activities SET
    status = 'COMPLETED',
    percent_complete = 100.0,
    physical_percent_complete = 100.0,
    duration_percent_complete = 100.0,
    actual_start_date = planned_start_date,
    actual_finish_date = planned_finish_date + INTERVAL '4 days',
    remaining_duration = 0.0,
    at_completion_duration = COALESCE(original_duration, 0.0) + 4.0,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-1.1','ACT-1.2','ACT-1.3','ACT-2.1','ACT-2.2','ACT-2.3');

-- ──────────── IN_PROGRESS — WBS-3 bituminous (72%) ───────────────
-- 3.1 DBM, 3.2 BC, 3.3 tack coat, 3.4 bitumen supply all tracking ~72%.
UPDATE activity.activities SET
    status = 'IN_PROGRESS',
    percent_complete = CASE code
        WHEN 'ACT-3.1' THEN 75.0
        WHEN 'ACT-3.2' THEN 70.0
        WHEN 'ACT-3.3' THEN 72.0
        WHEN 'ACT-3.4' THEN 72.0
    END,
    physical_percent_complete = CASE code
        WHEN 'ACT-3.1' THEN 75.0
        WHEN 'ACT-3.2' THEN 70.0
        WHEN 'ACT-3.3' THEN 72.0
        WHEN 'ACT-3.4' THEN 72.0
    END,
    duration_percent_complete = CASE code
        WHEN 'ACT-3.1' THEN 78.0
        WHEN 'ACT-3.2' THEN 72.0
        WHEN 'ACT-3.3' THEN 75.0
        WHEN 'ACT-3.4' THEN 75.0
    END,
    actual_start_date = planned_start_date + INTERVAL '6 days',
    actual_finish_date = NULL,
    remaining_duration = CASE code
        WHEN 'ACT-3.1' THEN 30.0
        WHEN 'ACT-3.2' THEN 36.0
        WHEN 'ACT-3.3' THEN 14.0
        WHEN 'ACT-3.4' THEN 22.0
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-3.1','ACT-3.2','ACT-3.3','ACT-3.4');

-- ──────────── IN_PROGRESS — WBS-4 drainage (80%) ────────────────
UPDATE activity.activities SET
    status = 'IN_PROGRESS',
    percent_complete = CASE code
        WHEN 'ACT-4.1' THEN 82.0
        WHEN 'ACT-4.2' THEN 78.0
        WHEN 'ACT-4.3' THEN 81.0
        WHEN 'ACT-4.4' THEN 80.0
    END,
    physical_percent_complete = CASE code
        WHEN 'ACT-4.1' THEN 82.0
        WHEN 'ACT-4.2' THEN 78.0
        WHEN 'ACT-4.3' THEN 81.0
        WHEN 'ACT-4.4' THEN 80.0
    END,
    duration_percent_complete = CASE code
        WHEN 'ACT-4.1' THEN 85.0
        WHEN 'ACT-4.2' THEN 80.0
        WHEN 'ACT-4.3' THEN 82.0
        WHEN 'ACT-4.4' THEN 82.0
    END,
    actual_start_date = planned_start_date + INTERVAL '5 days',
    actual_finish_date = NULL,
    remaining_duration = CASE code
        WHEN 'ACT-4.1' THEN 12.0
        WHEN 'ACT-4.2' THEN 18.0
        WHEN 'ACT-4.3' THEN 14.0
        WHEN 'ACT-4.4' THEN 16.0
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-4.1','ACT-4.2','ACT-4.3','ACT-4.4');

-- ──────────── IN_PROGRESS — WBS-5 road furniture (30%) ──────────
UPDATE activity.activities SET
    status = 'IN_PROGRESS',
    percent_complete = CASE code
        WHEN 'ACT-5.1' THEN 32.0
        WHEN 'ACT-5.2' THEN 28.0
        WHEN 'ACT-5.3' THEN 30.0
        WHEN 'ACT-5.4' THEN 30.0
    END,
    physical_percent_complete = CASE code
        WHEN 'ACT-5.1' THEN 32.0
        WHEN 'ACT-5.2' THEN 28.0
        WHEN 'ACT-5.3' THEN 30.0
        WHEN 'ACT-5.4' THEN 30.0
    END,
    duration_percent_complete = CASE code
        WHEN 'ACT-5.1' THEN 35.0
        WHEN 'ACT-5.2' THEN 30.0
        WHEN 'ACT-5.3' THEN 32.0
        WHEN 'ACT-5.4' THEN 33.0
    END,
    actual_start_date = planned_start_date + INTERVAL '12 days',
    actual_finish_date = NULL,
    remaining_duration = CASE code
        WHEN 'ACT-5.1' THEN 38.0
        WHEN 'ACT-5.2' THEN 50.0
        WHEN 'ACT-5.3' THEN 42.0
        WHEN 'ACT-5.4' THEN 46.0
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-5.1','ACT-5.2','ACT-5.3','ACT-5.4');

-- ──────────── IN_PROGRESS — WBS-6 structures (65%) ──────────────
UPDATE activity.activities SET
    status = 'IN_PROGRESS',
    percent_complete = CASE code
        WHEN 'ACT-6.1' THEN 70.0
        WHEN 'ACT-6.2' THEN 65.0
        WHEN 'ACT-6.3' THEN 65.0
        WHEN 'ACT-6.4' THEN 50.0
    END,
    physical_percent_complete = CASE code
        WHEN 'ACT-6.1' THEN 70.0
        WHEN 'ACT-6.2' THEN 65.0
        WHEN 'ACT-6.3' THEN 65.0
        WHEN 'ACT-6.4' THEN 50.0
    END,
    duration_percent_complete = CASE code
        WHEN 'ACT-6.1' THEN 72.0
        WHEN 'ACT-6.2' THEN 68.0
        WHEN 'ACT-6.3' THEN 68.0
        WHEN 'ACT-6.4' THEN 55.0
    END,
    actual_start_date = planned_start_date + INTERVAL '8 days',
    actual_finish_date = NULL,
    remaining_duration = CASE code
        WHEN 'ACT-6.1' THEN 26.0
        WHEN 'ACT-6.2' THEN 34.0
        WHEN 'ACT-6.3' THEN 30.0
        WHEN 'ACT-6.4' THEN 48.0
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-6.1','ACT-6.2','ACT-6.3','ACT-6.4');

-- ──────────── WBS-7 misc (10%, mostly not started) ──────────────
-- Site-office / batching-plant setup (ACT-7.3) is fully complete;
-- everything else is still NOT_STARTED.
UPDATE activity.activities SET
    status = 'COMPLETED',
    percent_complete = 100.0,
    physical_percent_complete = 100.0,
    duration_percent_complete = 100.0,
    actual_start_date = planned_start_date,
    actual_finish_date = planned_finish_date + INTERVAL '2 days',
    remaining_duration = 0.0,
    at_completion_duration = COALESCE(original_duration, 0.0) + 2.0,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code = 'ACT-7.3';

UPDATE activity.activities SET
    status = 'NOT_STARTED',
    percent_complete = 0.0,
    physical_percent_complete = 0.0,
    duration_percent_complete = 0.0,
    actual_start_date = NULL,
    actual_finish_date = NULL,
    remaining_duration = COALESCE(original_duration, 0.0),
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
  AND code IN ('ACT-7.1','ACT-7.2','ACT-7.4','ACT-7.5');
