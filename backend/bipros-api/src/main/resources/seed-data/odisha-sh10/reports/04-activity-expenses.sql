-- 04-activity-expenses.sql
-- Per-activity expense rows totalling BAC = ₹452.5 Cr (4,525,000,000).
-- Cost behaviour by package:
--   WBS-1, WBS-2 — COMPLETED: actual ≈ budget × 1.04 (4% overrun)
--   WBS-3, WBS-4, WBS-6 — IN_PROGRESS: actual ≈ budget × pct_complete × 1.05
--   WBS-5 — IN_PROGRESS at 30%: actual reflects ramp-up cost
--   WBS-7 — mostly NOT_STARTED (actual = 0); 7.3 site-office is COMPLETED
-- at_completion_cost embeds 4–5% forward-looking overrun, driving the
-- project EAC ≈ ₹4,773.8 Cr (matches 06-evm-calculations.sql last row).
-- All amounts in rupees.

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
    -- ─── WBS-1 Earthwork (₹65 Cr, COMPLETED) ────────────────────────
    ('ACT-1.1', 'Earthwork Excavation',           'Mass excavation in soft + hard strata, all classifications',          220000000.00, 228800000.00, 228800000.00),
    ('ACT-1.2', 'Embankment & Compaction',        'Embankment construction with approved fill (95% MDD)',                270000000.00, 280800000.00, 280800000.00),
    ('ACT-1.3', 'Sub-grade Preparation',          'Sub-grade prepared and compacted to 95% MDD',                         160000000.00, 166400000.00, 166400000.00),
    -- ─── WBS-2 Sub-base (₹85 Cr, COMPLETED) ─────────────────────────
    ('ACT-2.1', 'GSB Layer',                      'Granular Sub-Base — 200 mm compacted thickness',                      380000000.00, 395200000.00, 395200000.00),
    ('ACT-2.2', 'WMM Layer',                      'Wet Mix Macadam — 250 mm compacted thickness',                        400000000.00, 416000000.00, 416000000.00),
    ('ACT-2.3', 'Prime Coat',                     'Bituminous emulsion prime coat over WMM',                              70000000.00,  72800000.00,  72800000.00),
    -- ─── WBS-3 Bituminous (₹140 Cr, IN_PROGRESS ~72%) ───────────────
    ('ACT-3.1', 'DBM Layer',                      'Dense Bituminous Macadam — 50 mm',                                    560000000.00, 460000000.00, 588000000.00),
    ('ACT-3.2', 'BC Wearing Course',              'Bituminous Concrete wearing course — 40 mm',                          460000000.00, 338100000.00, 483000000.00),
    ('ACT-3.3', 'Tack Coat',                      'Bituminous emulsion tack coat between layers',                         50000000.00,  37800000.00,  52500000.00),
    ('ACT-3.4', 'Bitumen VG-30 Supply',           'Bitumen ex-IOCL Paradip including transit + handling losses',         330000000.00, 268000000.00, 346500000.00),
    -- ─── WBS-4 Drainage (₹40 Cr, IN_PROGRESS 80%) ───────────────────
    ('ACT-4.1', 'Catch Water Drains',             'Random Rubble masonry catchment drains',                               60000000.00,  51660000.00,  63000000.00),
    ('ACT-4.2', 'Box Culverts',                   'RCC M25 box culverts (1.5 × 1.5 m, 3 m span)',                        180000000.00, 147420000.00, 189000000.00),
    ('ACT-4.3', 'Pipe Culverts',                  'NP3 RCC 900 mm dia pipe culverts',                                     90000000.00,  76550000.00,  94500000.00),
    ('ACT-4.4', 'Side Drains',                    'Earth side drains with random rubble lining',                          70000000.00,  58800000.00,  73500000.00),
    -- ─── WBS-5 Road Furniture (₹25 Cr, IN_PROGRESS 30%) ─────────────
    ('ACT-5.1', 'Thermoplastic Road Marking',     'Thermoplastic road markings — IRC:35 spec',                            50000000.00,  16800000.00,  52500000.00),
    ('ACT-5.2', 'Retro-reflective Signboards',    'Retro-reflective sign boards with overhead gantries',                 100000000.00,  29400000.00, 105000000.00),
    ('ACT-5.3', 'Kerb Stones',                    'Precast kerb stones along median',                                     40000000.00,  12600000.00,  42000000.00),
    ('ACT-5.4', 'W-beam Crash Barriers',          'W-beam metal crash barriers along approach embankments',               60000000.00,  18900000.00,  63000000.00),
    -- ─── WBS-6 Structures (₹72.5 Cr, IN_PROGRESS 65%) ───────────────
    ('ACT-6.1', 'Bridge Substructure',            'RCC M30 pier & abutment foundations (2 minor bridges)',               180000000.00, 142000000.00, 189000000.00),
    ('ACT-6.2', 'Bridge Superstructure',          'PSC girder launching + RCC deck slab',                                160000000.00, 109200000.00, 168000000.00),
    ('ACT-6.3', 'ROB Substructure (Cuttack)',     'Road-Over-Bridge substructure at Cuttack approach',                   175000000.00, 119440000.00, 183750000.00),
    ('ACT-6.4', 'ROB Superstructure (Cuttack)',   'Road-Over-Bridge superstructure (4 spans) at Cuttack approach',       210000000.00, 110250000.00, 220500000.00),
    -- ─── WBS-7 Misc (₹25 Cr, mostly NOT_STARTED) ───────────────────
    ('ACT-7.1', 'Utility Shifting',               'Water + power + telecom utility shifting (OWD-borne)',                 90000000.00,         0.00,  94500000.00),
    ('ACT-7.2', 'Toll Plaza Approach Civil',      'Civil works at toll plaza approach',                                   75000000.00,         0.00,  78750000.00),
    ('ACT-7.3', 'Site Office & Batching Plant',   'Site office, lab and batching plant setup — fully recoverable',        40000000.00,  41600000.00,  41600000.00),
    ('ACT-7.4', 'As-built Drawings & Handover',   'As-built drawings + handover documentation pack',                      15000000.00,         0.00,  15750000.00),
    ('ACT-7.5', 'Punch List & Warranty Reserve',  'Punch list rectification + warranty period reserve',                   30000000.00,         0.00,  31500000.00)
) AS m(act_code, name, description, budgeted_cost, actual_cost, at_completion_cost)
JOIN activity.activities a ON a.code = m.act_code
 AND a.project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001');
