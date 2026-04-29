-- BNK reports/06-milestones.sql — Five contract milestones for 6155.
--
-- Following the NHAI pattern, milestones are stored as zero-duration rows in
-- activity.activities with activity_type = START_MILESTONE / FINISH_MILESTONE.
-- These drive the Milestones tab and the "Next milestone" status tile.
--
-- 1. Mobilisation Complete                — done   (2024-09-30)
-- 2. 25% Physical Progress (Earthworks)   — done   (2025-04-30)
-- 3. 50% Physical Progress (Pavement L1)  — done   (2025-10-31)
-- 4. 75% Physical Progress (BC Layer)     — pending(2026-04-30)  ← upcoming
-- 5. Project Handover to MoTC             — pending(2026-08-31)
--
-- Pinned to WBS-ROOT (the project root WBS package) — Agent 2 seeds that node.

INSERT INTO activity.activities (
    id, created_at, updated_at, version,
    project_id, wbs_node_id,
    code, name, description,
    activity_type, status, duration_type, percent_complete_type,
    original_duration, remaining_duration,
    percent_complete, physical_percent_complete,
    planned_start_date, planned_finish_date,
    actual_start_date,  actual_finish_date,
    is_critical, sort_order
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, w.id,
       m.code, m.name, m.description,
       m.activity_type, m.status,
       'FIXED_DURATION_AND_UNITS', 'PHYSICAL',
       0.0, 0.0,
       m.pct, m.pct,
       m.planned_date, m.planned_date,
       m.actual_date,  m.actual_date,
       TRUE, m.sort_order
FROM (VALUES
    ('MS-001', 'Mobilisation Complete',
     'Site set-up, batching plant + crusher hot, contractor camp + worker housing certified by HSE. Formal NTP issued by MoTC.',
     'START_MILESTONE',  'COMPLETED',
     DATE '2024-09-30', DATE '2024-09-30', 100.0, 1),
    ('MS-002', '25% Physical Progress — Earthworks Substantially Complete',
     'Mass excavation + embankment to subgrade level achieved across both carriageways for full 41 km Barka → Nakhal stretch.',
     'FINISH_MILESTONE', 'COMPLETED',
     DATE '2025-04-30', DATE '2025-05-08', 100.0, 2),
    ('MS-003', '50% Physical Progress — GSB/WMM Layer 1 Complete',
     'Granular Sub-Base + Wet Mix Macadam placed and compacted across full corridor; pavement substructure ready for DBM.',
     'FINISH_MILESTONE', 'COMPLETED',
     DATE '2025-10-31', DATE '2025-11-12', 100.0, 3),
    ('MS-004', '75% Physical Progress — BC Layer Complete',
     'Bituminous Concrete wearing course laid; ready for road furniture, signage, and OETC HT crossing relocation.',
     'FINISH_MILESTONE', 'NOT_STARTED',
     DATE '2026-04-30', NULL,                 50.0, 4),
    ('MS-005', 'Project Handover to MoTC',
     'Substantial completion certificate, punch-list closure, and handover to Ministry of Transport, Communications & IT.',
     'FINISH_MILESTONE', 'NOT_STARTED',
     DATE '2026-08-31', NULL,                  0.0, 5)
) AS m(code, name, description, activity_type, status, planned_date, actual_date, pct, sort_order)
JOIN project.projects p ON p.code = '6155'
JOIN project.wbs_nodes w ON w.code = 'WBS-ROOT' AND w.project_id = p.id
WHERE NOT EXISTS (
    SELECT 1 FROM activity.activities a
    WHERE  a.project_id = p.id
      AND  a.code       = m.code
);
