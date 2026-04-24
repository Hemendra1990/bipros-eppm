-- 03-resource-assignments.sql
-- Link 10 activities to the 7 seeded equipment / material / sub-contract
-- resources.  Fills the Resources tab and drops the DCMA-14
-- "Tasks without resources" failure.  Rates and costs line up with the
-- BAC distribution used by 04-activity-expenses.sql.

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
    ('ACT-1.1', 'NH48-EQ-EXCAVATORJCB210',      1600.0, 1680.0,  100000000.0, 105000000.0),
    ('ACT-1.2', 'NH48-EQ-TIPPERDUMPER10T',      1440.0, 1480.0,   60000000.0,  62000000.0),
    ('ACT-2.1', 'NH48-MT-GSBMATERIAL',           640.0,  656.0,  150000000.0, 152000000.0),
    ('ACT-2.2', 'NH48-MT-WMMAGGREGATE',          480.0,  496.0,  120000000.0, 122000000.0),
    ('ACT-3.1', 'NH48-EQ-HOTMIXPLANT60TPH',      400.0,  260.0,  250000000.0, 162500000.0),
    ('ACT-3.2', 'NH48-MT-BITUMENVG30',           360.0,  126.0,  180000000.0,  63000000.0),
    ('ACT-4.2', 'NH48-SC-CONCRETECULVERT',       360.0,  400.0,   80000000.0,  88000000.0),
    ('ACT-5.1', 'NH48-SC-ROADMARKING',           200.0,   30.0,   30000000.0,   4500000.0),
    ('ACT-6.1', 'NH48-MT-CEMENTOPC43',           480.0,  520.0,  200000000.0, 215000000.0),
    ('ACT-6.2', 'NH48-EQ-CONCRETEBATCHINGPLAN',  440.0,  464.0,  220000000.0, 230000000.0)
) AS m(act_code, res_code, planned_units, actual_units, planned_cost, actual_cost)
JOIN activity.activities a ON a.code = m.act_code
 AND a.project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001')
JOIN resource.resources r ON r.code = m.res_code;
