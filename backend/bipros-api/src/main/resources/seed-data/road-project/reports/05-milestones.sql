-- 05-milestones.sql
-- Four contract milestones: 2 already hit (kickoff + earthwork handover),
-- 2 upcoming (bituminous and final handover).  Drives the Milestones tab
-- and the "Next milestone" tile on the Status panel.

INSERT INTO activity.activities (
    id, created_at, updated_at, version,
    project_id, wbs_node_id,
    code, name, description,
    activity_type, status, duration_type, percent_complete_type,
    original_duration, remaining_duration, percent_complete, physical_percent_complete,
    planned_start_date, planned_finish_date,
    actual_start_date, actual_finish_date,
    is_critical, sort_order
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.id, w.id,
    m.code, m.name, m.description,
    m.activity_type, m.status,
    'FIXED_DURATION_AND_UNITS', 'PHYSICAL',
    0.0, m.remaining_duration,
    m.pct, m.pct,
    m.planned_date, m.planned_date,
    m.actual_date, m.actual_date,
    m.is_critical, m.sort_order
FROM (VALUES
    ('MS-001', 'Project Kickoff',                   'Mobilisation complete; formal site handover from NHAI', 'START_MILESTONE',  'COMPLETED',    DATE '2025-01-01', DATE '2025-01-01',   0.0, 100.0, TRUE, 1, 'WBS-ROOT'),
    ('MS-002', 'Earthwork Handover',                'Embankment and excavation declared substantially complete',   'FINISH_MILESTONE', 'COMPLETED',    DATE '2025-07-30', DATE '2025-08-02',   0.0, 100.0, TRUE, 2, 'WBS-1'),
    ('MS-003', 'Bituminous Surfacing Complete',     'DBM + BC layers ready for signage and road furniture',        'FINISH_MILESTONE', 'NOT_STARTED',  DATE '2025-09-10', NULL,                35.0,   0.0, TRUE, 3, 'WBS-3'),
    ('MS-004', 'Project Handover',                  'Substantial completion and handover to NHAI',                 'FINISH_MILESTONE', 'NOT_STARTED',  DATE '2026-12-31', NULL,               250.0,   0.0, TRUE, 4, 'WBS-ROOT')
) AS m(code, name, description, activity_type, status, planned_date, actual_date, remaining_duration, pct, is_critical, sort_order, wbs_code)
JOIN project.projects p ON p.code = 'BIPROS/NHAI/RJ/2025/001'
JOIN project.wbs_nodes w ON w.code = m.wbs_code AND w.project_id = p.id;
