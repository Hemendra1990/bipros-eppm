-- BNK reports/15-financial-periods.sql — 8 quarterly periods FY24Q4..FY26Q3.
--
-- Plan §4.27: 8 quarterly financial periods spanning the project window
-- (Sep 2024 → Aug 2026).  Latest 1–2 left OPEN (is_closed = false) so the
-- user can post new StorePeriodPerformance rows.  Older periods CLOSED.
--
-- "FY" follows MoTC fiscal year — Jan–Dec calendar in Oman, but the project
-- prefers the natural project window so we label periods by calendar quarter
-- mapped to project FY: FY24Q4 = Oct–Dec 2024, etc.
--
-- FinancialPeriod has no project_id column — periods are tenant-wide.
-- We use the natural unique key (name) via NOT EXISTS.

INSERT INTO cost.financial_periods (
    id, created_at, updated_at, version,
    name, start_date, end_date, period_type, is_closed, sort_order
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.name, p.start_date, p.end_date, 'QUARTERLY', p.is_closed, p.sort_order
FROM (VALUES
    ('BNK FY24 Q4', DATE '2024-09-01', DATE '2024-12-31', TRUE,  1),
    ('BNK FY25 Q1', DATE '2025-01-01', DATE '2025-03-31', TRUE,  2),
    ('BNK FY25 Q2', DATE '2025-04-01', DATE '2025-06-30', TRUE,  3),
    ('BNK FY25 Q3', DATE '2025-07-01', DATE '2025-09-30', TRUE,  4),
    ('BNK FY25 Q4', DATE '2025-10-01', DATE '2025-12-31', TRUE,  5),
    ('BNK FY26 Q1', DATE '2026-01-01', DATE '2026-03-31', TRUE,  6),
    ('BNK FY26 Q2', DATE '2026-04-01', DATE '2026-06-30', FALSE, 7),  -- OPEN
    ('BNK FY26 Q3', DATE '2026-07-01', DATE '2026-08-31', FALSE, 8)   -- OPEN
) AS p(name, start_date, end_date, is_closed, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM cost.financial_periods fp WHERE fp.name = p.name
);
