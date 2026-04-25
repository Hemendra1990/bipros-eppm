-- 07-cash-flow-forecasts.sql
-- 29 monthly cash-flow periods covering the full project window
-- (Aug 2024 → Dec 2026).  Planned curve is S-shaped, peaking around the
-- bituminous + structures campaign in early 2026 at ~₹36 Cr/month.
-- Actual runs through April 2026 with cumulative ≈ ₹358.2 Cr (matches the
-- AC in 06-evm-calculations.sql); forecast from May 2026 onwards lands at
-- the EAC ≈ ₹477.38 Cr by December 2026.
-- All amounts in rupees.

INSERT INTO cost.cash_flow_forecasts (
    id, created_at, updated_at, version,
    project_id, period,
    planned_amount, actual_amount, forecast_amount,
    cumulative_planned, cumulative_actual, cumulative_forecast
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.id, c.period,
    c.planned, c.actual, c.forecast,
    c.cum_planned, c.cum_actual, c.cum_forecast
FROM (VALUES
    ('2024-08',   10000000.00,           0.00,           NULL,   10000000.00,           0.00,           NULL),
    ('2024-09',   30000000.00,   35000000.00,           NULL,   40000000.00,   35000000.00,           NULL),
    ('2024-10',   40000000.00,   40000000.00,           NULL,   80000000.00,   75000000.00,           NULL),
    ('2024-11',   60000000.00,   65000000.00,           NULL,  140000000.00,  140000000.00,           NULL),
    ('2024-12',   80000000.00,   85000000.00,           NULL,  220000000.00,  225000000.00,           NULL),
    ('2025-01',  100000000.00,   95000000.00,           NULL,  320000000.00,  320000000.00,           NULL),
    ('2025-02',  120000000.00,  120000000.00,           NULL,  440000000.00,  440000000.00,           NULL),
    ('2025-03',  140000000.00,  135000000.00,           NULL,  580000000.00,  575000000.00,           NULL),
    ('2025-04',  170000000.00,  165000000.00,           NULL,  750000000.00,  740000000.00,           NULL),
    ('2025-05',  190000000.00,  185000000.00,           NULL,  940000000.00,  925000000.00,           NULL),
    ('2025-06',  200000000.00,  205000000.00,           NULL, 1140000000.00, 1130000000.00,           NULL),
    ('2025-07',  200000000.00,  200000000.00,           NULL, 1340000000.00, 1330000000.00,           NULL),
    ('2025-08',  200000000.00,  200000000.00,           NULL, 1540000000.00, 1530000000.00,           NULL),
    ('2025-09',  200000000.00,  200000000.00,           NULL, 1740000000.00, 1730000000.00,           NULL),
    ('2025-10',  210000000.00,  210000000.00,           NULL, 1950000000.00, 1940000000.00,           NULL),
    ('2025-11',  210000000.00,  210000000.00,           NULL, 2160000000.00, 2150000000.00,           NULL),
    ('2025-12',  220000000.00,  210000000.00,           NULL, 2380000000.00, 2360000000.00,           NULL),
    ('2026-01',  260000000.00,  260000000.00,           NULL, 2640000000.00, 2620000000.00,           NULL),
    ('2026-02',  280000000.00,  280000000.00,           NULL, 2920000000.00, 2900000000.00,           NULL),
    ('2026-03',  320000000.00,  320000000.00,           NULL, 3240000000.00, 3220000000.00,           NULL),
    ('2026-04',  360000000.00,  362000000.00,           NULL, 3600000000.00, 3582000000.00,           NULL),
    ('2026-05',  200000000.00,           NULL,  230000000.00, 3800000000.00,           NULL, 3812000000.00),
    ('2026-06',  170000000.00,           NULL,  210000000.00, 3970000000.00,           NULL, 4022000000.00),
    ('2026-07',  140000000.00,           NULL,  180000000.00, 4110000000.00,           NULL, 4202000000.00),
    ('2026-08',  120000000.00,           NULL,  150000000.00, 4230000000.00,           NULL, 4352000000.00),
    ('2026-09',  100000000.00,           NULL,  120000000.00, 4330000000.00,           NULL, 4472000000.00),
    ('2026-10',   80000000.00,           NULL,  100000000.00, 4410000000.00,           NULL, 4572000000.00),
    ('2026-11',   65000000.00,           NULL,   80000000.00, 4475000000.00,           NULL, 4652000000.00),
    ('2026-12',   50000000.00,           NULL,  121800000.00, 4525000000.00,           NULL, 4773800000.00)
) AS c(period, planned, actual, forecast, cum_planned, cum_actual, cum_forecast)
CROSS JOIN (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001') AS p;
