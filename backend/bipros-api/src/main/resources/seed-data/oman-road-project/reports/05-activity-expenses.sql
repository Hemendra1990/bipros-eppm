-- BNK reports/05-activity-expenses.sql — ActivityExpense rows for 6155.
--
-- Plan §4.26: ~300 ActivityExpense rows, 80% coverage of activities with
-- percent_complete > 0.  Deterministic actual_cost = budget × pct_complete
-- × random(0.95, 1.10) where the "random" multiplier is derived from
-- HASHTEXT(activity_id) so reruns produce the same numbers.
--
-- Coverage rule: include the activity if MOD(rownum, 5) < 4 → 80%.
-- We skip activities with percent_complete = 0 (those have no actuals yet).
-- All amounts are OMR (3-decimal precision).
--
-- Idempotency: this table has no natural unique key, so we use NOT EXISTS
-- on (project_id, activity_id, name) to make the file rerunnable.

WITH proj AS (
    SELECT id FROM project.projects WHERE code = '6155'
),
candidates AS (
    SELECT a.id              AS activity_id,
           a.project_id,
           a.code             AS act_code,
           a.name             AS act_name,
           a.percent_complete,
           a.planned_start_date,
           a.planned_finish_date,
           a.actual_start_date,
           a.actual_finish_date,
           ROW_NUMBER() OVER (ORDER BY COALESCE(a.sort_order, 0), a.code) - 1 AS idx
    FROM   activity.activities a, proj
    WHERE  a.project_id = proj.id
      AND  COALESCE(a.percent_complete, 0) > 0
),
budgets AS (
    -- Pull the activity-level budget from the matching BOQ row by item_no = ACT-<n>
    SELECT c.activity_id,
           c.project_id,
           c.act_code,
           c.act_name,
           c.percent_complete,
           c.planned_start_date,
           c.planned_finish_date,
           c.actual_start_date,
           c.actual_finish_date,
           c.idx,
           COALESCE(b.boq_amount, 0)::numeric(19,3) AS budgeted_cost
    FROM   candidates c
    LEFT   JOIN project.boq_items b
           ON   b.project_id = c.project_id
           AND  'ACT-' || b.item_no = c.act_code
)
INSERT INTO cost.activity_expenses (
    id, created_at, updated_at, version,
    activity_id, project_id, cost_account_id,
    name, description, expense_category,
    budgeted_cost, actual_cost, remaining_cost, at_completion_cost,
    percent_complete,
    planned_start_date, planned_finish_date, actual_start_date, actual_finish_date,
    currency
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       b.activity_id, b.project_id, NULL,
       LEFT('Expense — ' || COALESCE(b.act_name, b.act_code), 255),
       'Direct work expense for activity ' || b.act_code || ' (BNK Barka–Nakhal)',
       'DIRECT_WORK',
       ROUND(b.budgeted_cost, 3),
       ROUND(b.budgeted_cost
             * (b.percent_complete::numeric / 100.0)
             * (0.95 + MOD(ABS(HASHTEXT(b.activity_id::text)), 16)::numeric / 100.0),
             3),
       GREATEST(0,
           ROUND(b.budgeted_cost
                 - b.budgeted_cost
                   * (b.percent_complete::numeric / 100.0)
                   * (0.95 + MOD(ABS(HASHTEXT(b.activity_id::text)), 16)::numeric / 100.0),
                 3)),
       ROUND(b.budgeted_cost
             * (1.0 + MOD(ABS(HASHTEXT(b.activity_id::text)), 8)::numeric / 100.0),
             3),
       b.percent_complete,
       b.planned_start_date, b.planned_finish_date,
       b.actual_start_date,  b.actual_finish_date,
       'OMR'
FROM   budgets b
WHERE  MOD(b.idx, 5) < 4    -- ~80% coverage
  AND  b.budgeted_cost > 0
  AND  NOT EXISTS (
        SELECT 1 FROM cost.activity_expenses e
        WHERE  e.project_id  = b.project_id
          AND  e.activity_id = b.activity_id
          AND  e.name        = LEFT('Expense — ' || COALESCE(b.act_name, b.act_code), 255)
       );
