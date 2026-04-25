-- 03-resource-assignments.sql
-- Link activities to the seeded equipment / material / sub-contract resources.
-- Resource codes follow the OdishaSh10ProjectSeeder.slug() rule:
-- "OD-<EQ|MT|SC>-<UPPER(alphanumeric, max 20 chars)>".  Fills the Resources
-- tab and clears the DCMA-14 "Tasks without resources" failure.  Costs line up
-- with the BAC distribution in 04-activity-expenses.sql.

INSERT INTO resource.resource_assignments (
    id, created_at, updated_at, version,
    project_id, activity_id, resource_id,
    planned_units, actual_units, remaining_units, at_completion_units,
    planned_cost, actual_cost, remaining_cost, at_completion_cost,
    planned_start_date, planned_finish_date, actual_start_date, actual_finish_date,
    rate_type
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    a.project_id, a.id, r.id,
    m.planned_units, m.actual_units,
    GREATEST(m.planned_units - m.actual_units, 0),
    GREATEST(m.planned_units, m.actual_units),
    m.planned_cost, m.actual_cost,
    GREATEST(m.planned_cost - m.actual_cost, 0),
    GREATEST(m.planned_cost, m.actual_cost),
    a.planned_start_date, a.planned_finish_date,
    a.actual_start_date, a.actual_finish_date,
    'BUDGETED'
FROM (VALUES
    -- Earthwork (WBS-1) — completed, mild overrun
    ('ACT-1.1', 'OD-EQ-EXCAVATORJCB220',     1820.0, 1900.0, 220000000.00, 230000000.00),
    ('ACT-1.2', 'OD-EQ-TIPPERTATA251810CUM', 1640.0, 1720.0, 270000000.00, 282000000.00),
    ('ACT-1.3', 'OD-EQ-MOTORGRADERCAT120K',   320.0,  335.0,  82000000.00,  85500000.00),
    -- Sub-base (WBS-2) — completed
    ('ACT-2.1', 'OD-MT-GSBAGGREGATE53118MM',  680.0,  700.0, 220000000.00, 229000000.00),
    ('ACT-2.2', 'OD-MT-WMMAGGREGATE530075MM', 720.0,  745.0, 280000000.00, 290000000.00),
    -- Bituminous (WBS-3) — in-progress at ~72%
    ('ACT-3.1', 'OD-EQ-HOTMIXPLANT120TPH',    560.0,  420.0, 350000000.00, 280000000.00),
    ('ACT-3.2', 'OD-MT-BITUMENVG30',          480.0,  340.0, 250000000.00, 184000000.00),
    -- Drainage (WBS-4) — in-progress at 80%
    ('ACT-4.2', 'OD-MT-CEMENTOPC43',          280.0,  225.0,  60000000.00,  50500000.00),
    -- Road furniture (WBS-5) — just started
    ('ACT-5.1', 'OD-MT-CRUSHEDSTONEAGGREGAT',  90.0,   28.0,  20000000.00,   6800000.00),
    -- Structures (WBS-6) — at 65%
    ('ACT-6.1', 'OD-MT-STEELTMTFE500D',       420.0,  295.0, 150000000.00, 108000000.00),
    ('ACT-6.2', 'OD-MT-CEMENTOPC43',          560.0,  370.0, 175000000.00, 118000000.00),
    -- Misc / utilities (WBS-7) — sub-contract anchors
    ('ACT-7.1', 'OD-SC-ELECTRICALWORKSTOLL',    1.0,    0.0,  85000000.00,         0.00),
    ('ACT-7.2', 'OD-SC-TOLLPLAZACIVILWORKS',    1.0,    0.2,  72000000.00,  15000000.00)
) AS m(act_code, res_code, planned_units, actual_units, planned_cost, actual_cost)
JOIN activity.activities a ON a.code = m.act_code
 AND a.project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001')
JOIN resource.resources r ON r.code = m.res_code;
