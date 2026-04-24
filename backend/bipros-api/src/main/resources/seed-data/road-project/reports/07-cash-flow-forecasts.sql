-- 07-cash-flow-forecasts.sql
-- 24 monthly cash-flow periods covering the full project window
-- (Jan 2025 → Dec 2026).  Planned curve is S-shaped around earthwork +
-- pavement peak.  Actual runs through Apr 2026; forecast continues
-- through Dec 2026 with slight overrun (matches the EAC in 06-).
-- All amounts in rupees (crores × 1e7).

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
    ('2025-01',  120000000.00,  125000000.00,           NULL,  120000000.00,  125000000.00,           NULL),
    ('2025-02',  150000000.00,  140000000.00,           NULL,  270000000.00,  265000000.00,           NULL),
    ('2025-03',  180000000.00,  190000000.00,           NULL,  450000000.00,  455000000.00,           NULL),
    ('2025-04',  220000000.00,  230000000.00,           NULL,  670000000.00,  685000000.00,           NULL),
    ('2025-05',  280000000.00,  300000000.00,           NULL,  950000000.00,  985000000.00,           NULL),
    ('2025-06',  350000000.00,  370000000.00,           NULL, 1300000000.00, 1355000000.00,           NULL),
    ('2025-07',  400000000.00,  420000000.00,           NULL, 1700000000.00, 1775000000.00,           NULL),
    ('2025-08',  380000000.00,  400000000.00,           NULL, 2080000000.00, 2175000000.00,           NULL),
    ('2025-09',  350000000.00,  360000000.00,           NULL, 2430000000.00, 2535000000.00,           NULL),
    ('2025-10',  300000000.00,  320000000.00,           NULL, 2730000000.00, 2855000000.00,           NULL),
    ('2025-11',  280000000.00,  300000000.00,           NULL, 3010000000.00, 3155000000.00,           NULL),
    ('2025-12',  250000000.00,  270000000.00,           NULL, 3260000000.00, 3425000000.00,           NULL),
    ('2026-01',  220000000.00,  240000000.00,           NULL, 3480000000.00, 3665000000.00,           NULL),
    ('2026-02',  200000000.00,  220000000.00,           NULL, 3680000000.00, 3885000000.00,           NULL),
    ('2026-03',  180000000.00,  200000000.00,           NULL, 3860000000.00, 4085000000.00,           NULL),
    ('2026-04',  150000000.00,  160000000.00,           NULL, 4010000000.00, 4245000000.00,           NULL),
    ('2026-05',  140000000.00,           NULL,  150000000.00, 4150000000.00,           NULL, 4395000000.00),
    ('2026-06',  120000000.00,           NULL,  130000000.00, 4270000000.00,           NULL, 4525000000.00),
    ('2026-07',  100000000.00,           NULL,  110000000.00, 4370000000.00,           NULL, 4635000000.00),
    ('2026-08',  100000000.00,           NULL,  110000000.00, 4470000000.00,           NULL, 4745000000.00),
    ('2026-09',  120000000.00,           NULL,  130000000.00, 4590000000.00,           NULL, 4875000000.00),
    ('2026-10',  100000000.00,           NULL,  110000000.00, 4690000000.00,           NULL, 4985000000.00),
    ('2026-11',   90000000.00,           NULL,  100000000.00, 4780000000.00,           NULL, 5085000000.00),
    ('2026-12',   70000000.00,           NULL,   80000000.00, 4850000000.00,           NULL, 5165000000.00)
) AS c(period, planned, actual, forecast, cum_planned, cum_actual, cum_forecast)
CROSS JOIN (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001') AS p;
