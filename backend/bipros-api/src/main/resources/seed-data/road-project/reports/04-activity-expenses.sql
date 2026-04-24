-- 04-activity-expenses.sql
-- Per-activity expense rows.  BAC totals ~₹485 Cr across the project.
-- Actual cost for COMPLETED/IN_PROGRESS activities shows mild overrun
-- (~7%), which drives CPI ≈ 0.95 once EVM snapshots are computed.
-- at_completion_cost includes forward-looking variance.

INSERT INTO cost.activity_expenses (
    id, created_at, updated_at, version,
    project_id, activity_id, name, description, expense_category,
    budgeted_cost, actual_cost, remaining_cost, at_completion_cost,
    percent_complete,
    planned_start_date, planned_finish_date, actual_start_date, actual_finish_date
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    a.project_id, a.id, m.name, m.description, 'DIRECT_WORK',
    m.budgeted_cost, m.actual_cost,
    GREATEST(m.budgeted_cost - m.actual_cost, 0),
    m.at_completion_cost,
    COALESCE(a.percent_complete, 0),
    a.planned_start_date, a.planned_finish_date, a.actual_start_date, a.actual_finish_date
FROM (VALUES
    ('ACT-1.1', 'Earthwork Excavation',      'Mass excavation — soft + hard strata',      850000000.00, 920000000.00, 920000000.00),
    ('ACT-1.2', 'Embankment & Compaction',   'Embankment construction with approved fill', 750000000.00, 778000000.00, 778000000.00),
    ('ACT-2.1', 'GSB Layer',                 'Granular Sub-Base — 150 mm compacted',        550000000.00, 565000000.00, 565000000.00),
    ('ACT-2.2', 'WMM Layer',                 'Wet Mix Macadam — 250 mm compacted',          450000000.00, 462000000.00, 462000000.00),
    ('ACT-3.1', 'DBM Layer',                 'Dense Bituminous Macadam — 50 mm',            600000000.00, 432000000.00, 635000000.00),
    ('ACT-3.2', 'BC Layer',                  'Bituminous Concrete wearing course — 40 mm', 350000000.00, 139000000.00, 368000000.00),
    ('ACT-4.1', 'Catch Water Drain',         'Stone masonry catchment drain',                50000000.00,  52000000.00,  52000000.00),
    ('ACT-4.2', 'Box Culvert',               'RCC M25 box culvert construction',            100000000.00, 108000000.00, 108000000.00),
    ('ACT-4.3', 'Pipe Culvert',              'NP3 900 mm pipe culvert',                      80000000.00,  83000000.00,  83000000.00),
    ('ACT-5.1', 'Road Marking',              'Thermoplastic road marking',                   40000000.00,   6200000.00,  42000000.00),
    ('ACT-5.2', 'Sign Boards',               'Retro-reflective sign boards + gantries',      30000000.00,         0.00,  31000000.00),
    ('ACT-5.3', 'Kerb Stone Laying',         'Precast kerb stones along median',             20000000.00,         0.00,  20500000.00),
    ('ACT-6.1', 'Bridge Substructure',       'RCC M30 pier & abutment foundations',         450000000.00, 478000000.00, 478000000.00),
    ('ACT-6.2', 'Bridge Superstructure',     'PSC girder launching + deck slab',            480000000.00, 499000000.00, 499000000.00),
    ('ACT-7.1', 'Miscellaneous & Provisional','Punch list items + site clearance',            50000000.00,         0.00,  52000000.00)
) AS m(act_code, name, description, budgeted_cost, actual_cost, at_completion_cost)
JOIN activity.activities a ON a.code = m.act_code
 AND a.project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001');
