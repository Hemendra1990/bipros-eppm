-- BNK reports/16-period-performance.sql — StorePeriodPerformance for 6155.
--
-- Plan §4.27: per-period {labor, nonlabor, material} cost rows, per-activity
-- for activities that are "active" within the period.  We define an activity
-- as "active in period P" if its planned window overlaps P.  Only IN_PROGRESS
-- and COMPLETED activities contribute (NOT_STARTED + CANCELLED contribute
-- nothing).
--
-- Allocation rule (deterministic):
--   per_period_total = activity_budget × (period_overlap_days / planned_days) × pct_complete/100
-- and split into:
--   60% nonlabor (equipment + plant)
--   25% labor
--   15% material
--
-- Idempotency: NOT EXISTS on (project_id, financial_period_id, activity_id).

WITH proj AS (
    SELECT id FROM project.projects WHERE code = '6155'
),
periods AS (
    SELECT id, start_date, end_date FROM cost.financial_periods
    WHERE  name LIKE 'BNK FY%'
),
acts AS (
    SELECT a.id, a.project_id,
           a.planned_start_date, a.planned_finish_date,
           COALESCE(a.percent_complete, 0) AS pct,
           COALESCE(b.boq_amount, 0)::numeric(19,3) AS budget
    FROM   activity.activities a
    JOIN   proj                ON a.project_id = proj.id
    LEFT   JOIN project.boq_items b
           ON   b.project_id = a.project_id
           AND  'ACT-' || b.item_no = a.code
    WHERE  a.status IN ('IN_PROGRESS', 'COMPLETED')
      AND  a.planned_start_date  IS NOT NULL
      AND  a.planned_finish_date IS NOT NULL
      AND  a.planned_finish_date > a.planned_start_date
),
allocations AS (
    SELECT a.id              AS activity_id,
           a.project_id,
           p.id              AS financial_period_id,
           a.budget,
           a.pct,
           GREATEST(0,
                    LEAST(p.end_date, a.planned_finish_date)
                  - GREATEST(p.start_date, a.planned_start_date)
                   )::numeric AS overlap_days,
           (a.planned_finish_date - a.planned_start_date)::numeric AS planned_days
    FROM   acts a
    CROSS  JOIN periods p
)
INSERT INTO cost.store_period_performance (
    id, created_at, updated_at, version,
    project_id, financial_period_id, activity_id,
    actual_labor_cost, actual_nonlabor_cost, actual_material_cost, actual_expense_cost,
    actual_labor_units, actual_nonlabor_units, actual_material_units,
    earned_value_cost, planned_value_cost
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       al.project_id, al.financial_period_id, al.activity_id,
       ROUND(period_share * 0.25, 3),                                 -- labor
       ROUND(period_share * 0.60, 3),                                 -- nonlabor
       ROUND(period_share * 0.15, 3),                                 -- material
       ROUND(period_share * 0.05, 3),                                 -- other expense
       ROUND(period_share * 0.25 / 4.5, 3),                           -- labor units (avg 4.5 OMR/h blended)
       ROUND(period_share * 0.60 / 35.0, 3),                          -- nonlabor units (avg 35 OMR/h plant)
       ROUND(period_share * 0.15 / 12.0, 3),                          -- material units (12 OMR avg unit)
       ROUND(period_share, 3),                                        -- earned value approx period share
       ROUND(period_planned, 3)                                       -- planned value approx
FROM (
    SELECT al.activity_id, al.project_id, al.financial_period_id,
           CASE WHEN al.planned_days > 0 AND al.overlap_days > 0
                THEN al.budget * (al.overlap_days / al.planned_days) * (al.pct::numeric / 100.0)
                ELSE 0::numeric
            END AS period_share,
           CASE WHEN al.planned_days > 0 AND al.overlap_days > 0
                THEN al.budget * (al.overlap_days / al.planned_days)
                ELSE 0::numeric
            END AS period_planned
    FROM allocations al
    WHERE al.overlap_days > 0
) AS al
WHERE period_share > 0
  AND NOT EXISTS (
        SELECT 1 FROM cost.store_period_performance s
        WHERE  s.project_id          = al.project_id
          AND  s.financial_period_id = al.financial_period_id
          AND  s.activity_id         = al.activity_id
      );
