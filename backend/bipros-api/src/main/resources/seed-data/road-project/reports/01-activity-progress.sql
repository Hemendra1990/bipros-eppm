-- 01-activity-progress.sql
-- Give the NH-48 project a realistic execution mix so the Tasks / Schedule /
-- EVM panels aren't all zero.  9 activities COMPLETED on or near plan,
-- 3 IN_PROGRESS with partial completion, 3 still NOT_STARTED (the later
-- signage / misc scope).  Data date reference: 2026-04-20.

UPDATE activity.activities SET
    status = 'COMPLETED',
    percent_complete = 100.0,
    physical_percent_complete = 100.0,
    duration_percent_complete = 100.0,
    actual_start_date = planned_start_date,
    actual_finish_date = planned_finish_date + INTERVAL '3 days',
    remaining_duration = 0.0,
    at_completion_duration = COALESCE(original_duration, 0.0) + 3.0,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001')
  AND code IN ('ACT-1.1','ACT-1.2','ACT-2.1','ACT-2.2','ACT-4.1','ACT-4.2','ACT-4.3','ACT-6.1','ACT-6.2');

UPDATE activity.activities SET
    status = 'IN_PROGRESS',
    percent_complete = CASE code
        WHEN 'ACT-3.1' THEN 65.0
        WHEN 'ACT-3.2' THEN 35.0
        WHEN 'ACT-5.1' THEN 15.0
    END,
    physical_percent_complete = CASE code
        WHEN 'ACT-3.1' THEN 65.0
        WHEN 'ACT-3.2' THEN 35.0
        WHEN 'ACT-5.1' THEN 15.0
    END,
    duration_percent_complete = CASE code
        WHEN 'ACT-3.1' THEN 70.0
        WHEN 'ACT-3.2' THEN 40.0
        WHEN 'ACT-5.1' THEN 20.0
    END,
    actual_start_date = planned_start_date + INTERVAL '10 days',
    actual_finish_date = NULL,
    remaining_duration = CASE code
        WHEN 'ACT-3.1' THEN 18.0
        WHEN 'ACT-3.2' THEN 28.0
        WHEN 'ACT-5.1' THEN 22.0
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001')
  AND code IN ('ACT-3.1','ACT-3.2','ACT-5.1');
