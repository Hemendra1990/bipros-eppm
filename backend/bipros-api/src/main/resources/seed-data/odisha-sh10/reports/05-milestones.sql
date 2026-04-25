-- 05-milestones.sql
-- Five OWD contract milestones — three already met (mobilisation, earthwork
-- handover, GSB/WMM substantial completion), two upcoming (DBM + final
-- handover).  Drives the Milestones tab and the "Next milestone" tile on the
-- Status panel.  Activity rows with activity_type=MILESTONE.

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
    ('MS-001', 'Mobilisation Complete',                'Site office, batching plant, lab and crew mobilisation complete; OWD formal site handover',  'START_MILESTONE',  'COMPLETED',    DATE '2024-09-15', DATE '2024-09-15',   0.0, 100.0, TRUE, 1, 'WBS-ROOT'),
    ('MS-002', 'Earthwork Handover',                   'Embankment + sub-grade declared substantially complete across the 28 km stretch',              'FINISH_MILESTONE', 'COMPLETED',    DATE '2025-06-30', DATE '2025-07-04',   0.0, 100.0, TRUE, 2, 'WBS-1'),
    ('MS-003', 'GSB/WMM Substantial Completion',       'Granular Sub-Base + Wet Mix Macadam layers handed over for bituminous works',                  'FINISH_MILESTONE', 'COMPLETED',    DATE '2025-12-31', DATE '2026-01-05',   0.0, 100.0, TRUE, 3, 'WBS-2'),
    ('MS-004', 'Bituminous Layer 1 (DBM) Complete',    'Dense Bituminous Macadam wearing course complete across full corridor',                        'FINISH_MILESTONE', 'NOT_STARTED',  DATE '2026-08-31', NULL,                128.0,   0.0, TRUE, 4, 'WBS-3'),
    ('MS-005', 'Project Handover',                     'Substantial completion + handover to OWD; defects-liability period commences',                 'FINISH_MILESTONE', 'NOT_STARTED',  DATE '2026-12-31', NULL,                250.0,   0.0, TRUE, 5, 'WBS-ROOT')
) AS m(code, name, description, activity_type, status, planned_date, actual_date, remaining_duration, pct, is_critical, sort_order, wbs_code)
JOIN project.projects p ON p.code = 'OWD/SH10/OD/2025/001'
JOIN project.wbs_nodes w ON w.code = m.wbs_code AND w.project_id = p.id;
