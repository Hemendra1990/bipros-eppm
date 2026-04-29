-- BNK reports/01-wbs-budget.sql — Lock WBS budget_crores rollup for project 6155.
-- The OmanRoadProjectSeeder computes leaf WBS budgets from BOQ qty × rate; this
-- file overrides those values so the Costs tab and the EVM BAC reconcile to a
-- known total of ~6.500 million OMR (BAC).
--
-- IMPORTANT: WbsNode.budget_crores is a numeric(14,2) field originally meant
-- for "crores of INR".  For OMR projects we co-opt it as "millions of OMR"
-- (frontend renders by project currency).  SUM(budget_crores) across the
-- project = 6.500 → 6,500,000 OMR BAC.
--
-- Distribution roughly mirrors the BOQ section weights from File 3 Sheet 5
-- (Preliminaries, Earthworks, Pavement, Bridges/Structures, Drainage,
--  Road Furniture, Plantation/Misc).  Leaf nodes only — root left NULL to
-- avoid double-count.

UPDATE project.wbs_nodes SET
    budget_crores = m.budget,
    updated_at    = CURRENT_TIMESTAMP
FROM (VALUES
    ('WBS-1', 0.30),   -- Preliminaries / Mobilisation
    ('WBS-2', 0.20),   -- Demolition & Site Clearance
    ('WBS-3', 1.40),   -- Earthworks (Cut/Fill/Embankment)
    ('WBS-4', 1.80),   -- Pavement (GSB/WMM/DBM/BC)
    ('WBS-5', 1.50),   -- Bridges & Structures (Wadi crossings)
    ('WBS-6', 0.50),   -- Drainage & Cross Drainage
    ('WBS-7', 0.35),   -- Road Furniture (Signs/Markings/Crash Barrier)
    ('WBS-8', 0.25),   -- Electrical / OETC HT relocation / ITS
    ('WBS-9', 0.20)    -- Plantation, Aesthetics & Misc Provisional
) AS m(wbs_code, budget)
WHERE project.wbs_nodes.code = m.wbs_code
  AND project.wbs_nodes.project_id = (SELECT id FROM project.projects WHERE code = '6155');
