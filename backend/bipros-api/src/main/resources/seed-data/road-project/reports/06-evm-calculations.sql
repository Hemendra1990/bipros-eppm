-- 06-evm-calculations.sql
-- 5 EVM snapshots across the project life showing a mildly drifting trend
-- (starts on schedule, slips to SPI ≈ 0.90 and CPI ≈ 0.95 by Apr 2026).
-- The most recent row drives the Status tiles (CPI / SPI / BAC / EAC).
-- All amounts in rupees.  BAC = ₹485 Cr = 4,850,000,000.

INSERT INTO evm.evm_calculations (
    id, created_at, updated_at, version,
    project_id, data_date,
    budget_at_completion, planned_value, earned_value, actual_cost,
    cost_variance, schedule_variance,
    cost_performance_index, schedule_performance_index,
    estimate_at_completion, estimate_to_complete, variance_at_completion,
    to_complete_performance_index,
    evm_technique, etc_method, performance_percent_complete
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.id, m.data_date,
    m.bac, m.pv, m.ev, m.ac,
    m.cv, m.sv,
    m.cpi, m.spi,
    m.eac, m.etc, m.vac,
    m.tcpi,
    'ACTIVITY_PERCENT_COMPLETE', 'CPI_BASED', m.pct_complete
FROM (VALUES
    (DATE '2025-04-30', 4850000000.00,  400000000.00,  395000000.00,  410000000.00, -15000000.00,  -5000000.00, 0.963, 0.988, 5036000000.00, 4626000000.00,  -186000000.00, 1.013,  8.14),
    (DATE '2025-07-31', 4850000000.00, 1500000000.00, 1450000000.00, 1520000000.00, -70000000.00, -50000000.00, 0.954, 0.967, 5084000000.00, 3564000000.00,  -234000000.00, 1.020, 29.90),
    (DATE '2025-10-31', 4850000000.00, 2500000000.00, 2400000000.00, 2600000000.00,-200000000.00,-100000000.00, 0.923, 0.960, 5255000000.00, 2655000000.00,  -405000000.00, 1.087, 49.48),
    (DATE '2026-01-31', 4850000000.00, 3700000000.00, 3500000000.00, 3820000000.00,-320000000.00,-200000000.00, 0.916, 0.946, 5296000000.00, 1476000000.00,  -446000000.00, 1.312, 72.16),
    (DATE '2026-04-20', 4850000000.00, 4750000000.00, 4280000000.00, 4520000000.00,-240000000.00,-470000000.00, 0.947, 0.901, 5124000000.00,  604000000.00,  -274000000.00, 1.728, 88.25)
) AS m(data_date, bac, pv, ev, ac, cv, sv, cpi, spi, eac, etc, vac, tcpi, pct_complete)
CROSS JOIN (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001') AS p;
