-- BNK reports/17-monte-carlo.sql — Monte Carlo simulation for 6155.
--
-- Plan §4.42: 1 simulation + 4 percentile MonteCarloResult rows
-- + 30 activity stats + 5 milestone stats + 12 cashflow buckets
-- + 12 risk contributions.
--
-- Drives the /predictions tab (P50/P75/P90/P95 cards) and the cost-CDF
-- chart on the EVM Dashboard.
--
-- BAC = 6,500,000 OMR; baseline duration = 730 days (project span).
-- Idempotency: a deterministic simulation_name uniquely identifies the
-- simulation; nested entities use the simulation_id, which we resolve via
-- the same name.

-- ── 1. Simulation row ─────────────────────────────────────────────────────
INSERT INTO risk.monte_carlo_simulations (
    id, created_at, updated_at, version,
    project_id, simulation_name, iterations,
    p10duration, p25duration, confidencep50duration, p75duration, confidencep80duration,
    p90duration, p95duration, p99duration, mean_duration, stddev_duration,
    p10cost, p25cost, confidencep50cost, p75cost, confidencep80cost,
    p90cost, p95cost, p99cost, mean_cost, stddev_cost,
    baseline_duration, baseline_cost,
    data_date, iterations_completed, completed_at, status
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, 'BNK Monte Carlo Baseline 2026-04-29', 10000,
       720.0, 740.0, 760.0, 790.0, 810.0,
       820.0, 850.0, 900.0, 770.0, 30.0,
       6300000.000, 6420000.000, 6580000.000, 6760000.000, 6840000.000,
       6920000.000, 7080000.000, 7280000.000, 6620000.000, 240000.000,
       730.0, 6500000.000,
       DATE '2026-04-29', 10000, TIMESTAMP '2026-04-29 02:00:00+00:00', 'COMPLETED'
FROM project.projects p
WHERE p.code = '6155'
  AND NOT EXISTS (
        SELECT 1 FROM risk.monte_carlo_simulations sim
        WHERE  sim.project_id = p.id
          AND  sim.simulation_name = 'BNK Monte Carlo Baseline 2026-04-29'
      );

-- ── 2. Four percentile MonteCarloResult rows ──────────────────────────────
-- These are stored as plain percentile-tagged iterations.  The plan calls
-- for "4 percentile results" (P50/P75/P90/P95), one row each.
INSERT INTO risk.monte_carlo_results (
    id, simulation_id, iteration_number, project_duration, project_cost
)
SELECT gen_random_uuid(), sim.id, r.iteration, r.duration, r.cost
FROM (VALUES
    (5000, 760.0, 6580000.000),   -- P50
    (7500, 790.0, 6760000.000),   -- P75
    (9000, 820.0, 6920000.000),   -- P90
    (9500, 850.0, 7080000.000)    -- P95
) AS r(iteration, duration, cost)
JOIN risk.monte_carlo_simulations sim
     ON sim.simulation_name = 'BNK Monte Carlo Baseline 2026-04-29'
    AND sim.project_id = (SELECT id FROM project.projects WHERE code = '6155')
WHERE NOT EXISTS (
    SELECT 1 FROM risk.monte_carlo_results existing
    WHERE  existing.simulation_id    = sim.id
      AND  existing.iteration_number = r.iteration
);

-- ── 3. 30 activity stats ──────────────────────────────────────────────────
-- Top 30 activities by budget contribute to the criticality / sensitivity
-- analysis.  Deterministic synthetic stats based on sort order.
INSERT INTO risk.monte_carlo_activity_stats (
    id, created_at, updated_at, version,
    simulation_id, activity_id, activity_code, activity_name,
    criticality_index,
    duration_mean, duration_stddev, durationp10, durationp90,
    duration_sensitivity, cost_sensitivity, cruciality
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       sim.id, a.id, a.code, LEFT(a.name, 100),
       0.95 - (rn::numeric * 0.025),
       (a.original_duration::numeric * 1.05),
       (a.original_duration::numeric * 0.12),
       (a.original_duration::numeric * 0.85),
       (a.original_duration::numeric * 1.30),
       0.85 - (rn::numeric * 0.020),
       0.80 - (rn::numeric * 0.018),
       0.90 - (rn::numeric * 0.022)
FROM (
    SELECT a.id, a.code, a.name, a.original_duration,
           ROW_NUMBER() OVER (ORDER BY COALESCE(b.boq_amount, 0) DESC, a.code) AS rn
    FROM   activity.activities a
    JOIN   project.boq_items b
           ON   b.project_id = a.project_id
           AND  'ACT-' || b.item_no = a.code
    WHERE  a.project_id = (SELECT id FROM project.projects WHERE code = '6155')
      AND  a.status IN ('NOT_STARTED', 'IN_PROGRESS')
) AS a
JOIN risk.monte_carlo_simulations sim
     ON sim.simulation_name = 'BNK Monte Carlo Baseline 2026-04-29'
    AND sim.project_id = (SELECT id FROM project.projects WHERE code = '6155')
WHERE a.rn <= 30
  AND NOT EXISTS (
        SELECT 1 FROM risk.monte_carlo_activity_stats existing
        WHERE  existing.simulation_id = sim.id
          AND  existing.activity_id   = a.id
      );

-- ── 4. 5 milestone stats ──────────────────────────────────────────────────
-- One row per MS-001..MS-005 created by 06-milestones.sql.
INSERT INTO risk.monte_carlo_milestone_stats (
    id, created_at, updated_at, version,
    simulation_id, activity_id, activity_code, activity_name,
    planned_finish_date, p50finish_date, p80finish_date, p90finish_date,
    cdf_json
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       sim.id, m.id, m.code, LEFT(m.name, 100),
       m.planned_finish_date,
       m.planned_finish_date + INTERVAL '5 days',
       m.planned_finish_date + INTERVAL '14 days',
       m.planned_finish_date + INTERVAL '22 days',
       '[{"date":"' || (m.planned_finish_date)::text || '","p":0.50},'
       || '{"date":"' || (m.planned_finish_date + INTERVAL '14 days')::date || '","p":0.80},'
       || '{"date":"' || (m.planned_finish_date + INTERVAL '22 days')::date || '","p":0.90}]'
FROM activity.activities m
JOIN risk.monte_carlo_simulations sim
     ON sim.simulation_name = 'BNK Monte Carlo Baseline 2026-04-29'
    AND sim.project_id = (SELECT id FROM project.projects WHERE code = '6155')
WHERE m.project_id = sim.project_id
  AND m.code LIKE 'MS-%'
  AND NOT EXISTS (
        SELECT 1 FROM risk.monte_carlo_milestone_stats existing
        WHERE  existing.simulation_id = sim.id
          AND  existing.activity_id   = m.id
      );

-- ── 5. 12 monthly cash-flow buckets (May 2025 → Apr 2026) ────────────────
INSERT INTO risk.monte_carlo_cashflow_buckets (
    id, created_at, updated_at, version,
    simulation_id, period_end_date,
    baseline_cumulative, p10cumulative, p50cumulative, p80cumulative, p90cumulative
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       sim.id, b.period_end,
       b.base, b.p10, b.p50, b.p80, b.p90
FROM (VALUES
    (DATE '2025-05-31', 2250000.000, 2200000.000, 2280000.000, 2360000.000, 2400000.000),
    (DATE '2025-06-30', 2730000.000, 2680000.000, 2780000.000, 2870000.000, 2920000.000),
    (DATE '2025-07-31', 3150000.000, 3080000.000, 3220000.000, 3320000.000, 3380000.000),
    (DATE '2025-08-31', 3530000.000, 3460000.000, 3610000.000, 3720000.000, 3790000.000),
    (DATE '2025-09-30', 3860000.000, 3790000.000, 3950000.000, 4070000.000, 4150000.000),
    (DATE '2025-10-31', 4160000.000, 4080000.000, 4265000.000, 4400000.000, 4490000.000),
    (DATE '2025-11-30', 4420000.000, 4340000.000, 4535000.000, 4685000.000, 4790000.000),
    (DATE '2025-12-31', 4640000.000, 4560000.000, 4770000.000, 4925000.000, 5050000.000),
    (DATE '2026-01-31', 4840000.000, 4760000.000, 4985000.000, 5160000.000, 5290000.000),
    (DATE '2026-02-28', 5020000.000, 4940000.000, 5180000.000, 5360000.000, 5500000.000),
    (DATE '2026-03-31', 5180000.000, 5100000.000, 5355000.000, 5540000.000, 5690000.000),
    (DATE '2026-04-29', 5330000.000, 5250000.000, 5515000.000, 5710000.000, 5870000.000)
) AS b(period_end, base, p10, p50, p80, p90)
JOIN risk.monte_carlo_simulations sim
     ON sim.simulation_name = 'BNK Monte Carlo Baseline 2026-04-29'
    AND sim.project_id = (SELECT id FROM project.projects WHERE code = '6155')
WHERE NOT EXISTS (
    SELECT 1 FROM risk.monte_carlo_cashflow_buckets existing
    WHERE  existing.simulation_id  = sim.id
      AND  existing.period_end_date = b.period_end
);

-- ── 6. 12 risk contributions (one per Oman risk R-001..R-012) ────────────
INSERT INTO risk.monte_carlo_risk_contributions (
    id, created_at, updated_at, version,
    simulation_id, risk_id, risk_code, risk_title,
    occurrences, occurrence_rate, mean_duration_impact, mean_cost_impact,
    affected_activity_ids
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       sim.id, r.id, r.code, LEFT(r.title, 255),
       (10000 * COALESCE(r.contribution_rate, 0.5))::int,
       COALESCE(r.contribution_rate, 0.5),
       COALESCE(r.schedule_impact_days, 0)::numeric * 0.65,    -- mean impact = 65% of max
       COALESCE(r.cost_impact, 0) * 0.65,
       ''
FROM (
    SELECT rr.id, rr.code, rr.title, rr.cost_impact, rr.schedule_impact_days,
           CASE
               WHEN rr.rag = 'RED'    THEN 0.85
               WHEN rr.rag = 'AMBER'  THEN 0.55
               WHEN rr.rag = 'GREEN'  THEN 0.20
               ELSE                        0.40
           END AS contribution_rate
    FROM   risk.risks rr
    WHERE  rr.project_id = (SELECT id FROM project.projects WHERE code = '6155')
      AND  rr.code LIKE 'R-%'
) AS r
JOIN risk.monte_carlo_simulations sim
     ON sim.simulation_name = 'BNK Monte Carlo Baseline 2026-04-29'
    AND sim.project_id = (SELECT id FROM project.projects WHERE code = '6155')
WHERE NOT EXISTS (
    SELECT 1 FROM risk.monte_carlo_risk_contributions existing
    WHERE  existing.simulation_id = sim.id
      AND  existing.risk_id       = r.id
);
