-- 00-wbs-budget.sql
-- Align the WBS-level budget_crores with the activity-level BAC (₹485 Cr)
-- and the evm.evm_calculations snapshots.  The existing
-- NhaiRoadProjectSeeder computes these values from the placeholder BOQ
-- quantities × rates in the Excel workbook, which totals only ₹21.78 Cr —
-- unrealistically small for a 20 km 4-lane NHAI widening.
--
-- Without this alignment, /projects/{id}?tab=costs shows Total Budget
-- ₹21.78 Cr while /reports shows BAC ₹485 Cr, and the cost-variance
-- chart on the report renders ~13,800% variance bars.  Prefixed 00-
-- so it runs before activity-expense and EVM seeds.

-- Update leaf-level WBS packages only.  WBS-ROOT is intentionally left NULL
-- (the existing seeder leaves it NULL too) so that SUM(budget_crores)
-- across the project cleanly equals 485 Cr without double-counting.
UPDATE project.wbs_nodes SET
    budget_crores = m.budget,
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('WBS-1', 160.00),
    ('WBS-2', 100.00),
    ('WBS-3',  95.00),
    ('WBS-4',  23.00),
    ('WBS-5',   9.00),
    ('WBS-6',  93.00),
    ('WBS-7',   5.00)
) AS m(wbs_code, budget)
WHERE project.wbs_nodes.code = m.wbs_code
  AND project.wbs_nodes.project_id = (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001');
