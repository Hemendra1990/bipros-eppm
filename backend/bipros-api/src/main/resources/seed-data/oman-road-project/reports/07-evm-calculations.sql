-- BNK reports/07-evm-calculations.sql — 5 EVM history snapshots for 6155.
--
-- BAC = SUM(WBS leaf budget_crores) × 1,000,000 = 6,500,000 OMR.
-- Plan §5.5: drift CPI 1.02 → 0.95, SPI 1.0 → 0.90 across the project life,
-- mirroring the NHAI template.  The most recent row drives the Status tiles
-- (CPI / SPI / BAC / EAC) on the Reports → EVM tab.
--
-- Snapshot dates span the project window 2024-09-01 → 2026-08-31.
-- Data date reference: 2026-04-29.

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
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, m.data_date,
       m.bac, m.pv, m.ev, m.ac,
       m.cv, m.sv,
       m.cpi, m.spi,
       m.eac, m.etc, m.vac,
       m.tcpi,
       'ACTIVITY_PERCENT_COMPLETE', 'CPI_BASED', m.pct
FROM (VALUES
    -- date         BAC         PV          EV          AC          CV         SV         CPI    SPI    EAC          ETC          VAC         TCPI   pct
    (DATE '2024-12-31', 6500000.000, 250000.000, 255000.000, 250000.000,    5000.000,   5000.000, 1.020, 1.020, 6372549.020, 6122549.020,  127450.980, 1.000,  3.92),
    (DATE '2025-06-30', 6500000.000,1500000.000,1490000.000,1495000.000,   -5000.000, -10000.000, 0.997, 0.993, 6521812.080, 5026812.080,  -21812.080, 1.001, 22.92),
    (DATE '2025-12-31', 6500000.000,3200000.000,3120000.000,3210000.000,  -90000.000, -80000.000, 0.972, 0.975, 6687500.000, 3477500.000, -187500.000, 1.027, 48.00),
    (DATE '2026-03-31', 6500000.000,4400000.000,4180000.000,4350000.000, -170000.000,-220000.000, 0.961, 0.950, 6764354.067, 2414354.067, -264354.067, 1.080, 64.31),
    (DATE '2026-04-29', 6500000.000,4750000.000,4400000.000,4630000.000, -230000.000,-350000.000, 0.950, 0.926, 6840909.091, 2210909.091, -340909.091, 1.114, 67.69)
) AS m(data_date, bac, pv, ev, ac, cv, sv, cpi, spi, eac, etc, vac, tcpi, pct)
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
WHERE NOT EXISTS (
    SELECT 1 FROM evm.evm_calculations e
    WHERE  e.project_id = p.id
      AND  e.data_date  = m.data_date
);
