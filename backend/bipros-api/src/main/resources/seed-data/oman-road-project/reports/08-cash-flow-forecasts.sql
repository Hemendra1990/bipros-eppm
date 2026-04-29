-- BNK reports/08-cash-flow-forecasts.sql — 24 monthly cash-flow rows for 6155.
--
-- Project window 2024-09-01 → 2026-08-31 (24 months).  Planned curve is
-- S-shaped around peak earthwork + pavement (Apr–Aug 2025).  Actual runs
-- through Apr 2026 (data date 2026-04-29); forecast extends through Aug
-- 2026.  Total planned BAC = 6,500,000 OMR (matches §07 EVM BAC).

INSERT INTO cost.cash_flow_forecasts (
    id, created_at, updated_at, version,
    project_id, period,
    planned_amount, actual_amount, forecast_amount,
    cumulative_planned, cumulative_actual, cumulative_forecast
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, c.period,
       c.planned, c.actual, c.forecast,
       c.cum_planned, c.cum_actual, c.cum_forecast
FROM (VALUES
    -- period      planned       actual       forecast      cum_planned   cum_actual   cum_forecast
    ('2024-09',     50000.000,    52000.000,         NULL,    50000.000,    52000.000,         NULL),
    ('2024-10',    100000.000,   105000.000,         NULL,   150000.000,   157000.000,         NULL),
    ('2024-11',    150000.000,   148000.000,         NULL,   300000.000,   305000.000,         NULL),
    ('2024-12',    200000.000,   195000.000,         NULL,   500000.000,   500000.000,         NULL),
    ('2025-01',    250000.000,   260000.000,         NULL,   750000.000,   760000.000,         NULL),
    ('2025-02',    300000.000,   310000.000,         NULL,  1050000.000,  1070000.000,         NULL),
    ('2025-03',    350000.000,   355000.000,         NULL,  1400000.000,  1425000.000,         NULL),
    ('2025-04',    400000.000,   410000.000,         NULL,  1800000.000,  1835000.000,         NULL),
    ('2025-05',    450000.000,   460000.000,         NULL,  2250000.000,  2295000.000,         NULL),
    ('2025-06',    480000.000,   495000.000,         NULL,  2730000.000,  2790000.000,         NULL),
    ('2025-07',    420000.000,   430000.000,         NULL,  3150000.000,  3220000.000,         NULL),
    ('2025-08',    380000.000,   390000.000,         NULL,  3530000.000,  3610000.000,         NULL),
    ('2025-09',    330000.000,   340000.000,         NULL,  3860000.000,  3950000.000,         NULL),
    ('2025-10',    300000.000,   315000.000,         NULL,  4160000.000,  4265000.000,         NULL),
    ('2025-11',    260000.000,   270000.000,         NULL,  4420000.000,  4535000.000,         NULL),
    ('2025-12',    220000.000,   235000.000,         NULL,  4640000.000,  4770000.000,         NULL),
    ('2026-01',    200000.000,   215000.000,         NULL,  4840000.000,  4985000.000,         NULL),
    ('2026-02',    180000.000,   195000.000,         NULL,  5020000.000,  5180000.000,         NULL),
    ('2026-03',    160000.000,   175000.000,         NULL,  5180000.000,  5355000.000,         NULL),
    ('2026-04',    150000.000,   160000.000,         NULL,  5330000.000,  5515000.000,         NULL),
    ('2026-05',    140000.000,         NULL,   150000.000,  5470000.000,         NULL,  5665000.000),
    ('2026-06',    130000.000,         NULL,   140000.000,  5600000.000,         NULL,  5805000.000),
    ('2026-07',    120000.000,         NULL,   130000.000,  5720000.000,         NULL,  5935000.000),
    ('2026-08',     80000.000,         NULL,    90000.000,  5800000.000,         NULL,  6025000.000)
) AS c(period, planned, actual, forecast, cum_planned, cum_actual, cum_forecast)
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
WHERE NOT EXISTS (
    SELECT 1 FROM cost.cash_flow_forecasts f
    WHERE  f.project_id = p.id
      AND  f.period     = c.period
);
