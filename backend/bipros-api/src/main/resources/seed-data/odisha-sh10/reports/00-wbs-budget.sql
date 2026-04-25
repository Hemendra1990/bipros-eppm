-- 00-wbs-budget.sql
-- Align the WBS-level budget_crores with the activity-level BAC (₹452.5 Cr)
-- and the evm.evm_calculations snapshots.  The OdishaSh10ProjectSeeder already
-- writes the same numbers when the project is fresh, but this file makes the
-- alignment idempotent: re-running the report bundle on a half-seeded DB still
-- ends up with the canonical numbers, and any drift introduced by manual edits
-- is reset before EVM / cost-variance reports are computed.
--
-- WBS-ROOT is intentionally left untouched (the seeder leaves it NULL) so that
-- SUM(budget_crores) over the leaf packages cleanly equals 452.5 Cr without
-- double-counting through the root row.
UPDATE project.wbs_nodes SET
    budget_crores = m.budget,
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('WBS-1',  65.00),
    ('WBS-2',  85.00),
    ('WBS-3', 140.00),
    ('WBS-4',  40.00),
    ('WBS-5',  25.00),
    ('WBS-6',  72.50),
    ('WBS-7',  25.00)
) AS m(wbs_code, budget)
WHERE project.wbs_nodes.code = m.wbs_code
  AND project.wbs_nodes.project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001');
