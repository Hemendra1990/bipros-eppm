-- BNK reports/21-retention-projections.sql — Four supplemental tables:
--   • cost.retention_money            (4 rows: one per contract; 5% retained till DLP)
--   • cost.dpr_estimates              (5 rows: one per cost-category — civil, struct, elec, escalation, contingency)
--   • project.monthly_boq_projections (30 rows: 5 high-value BOQ items × 6 forward months 2026-05 → 2026-10)
--   • project.corridor_codes          (1 row: BNK corridor identifier prefix)

-- ─────────────────────────── Retention Money ───────────────────────────
-- Each contract retains 5% of contract value till DLP. Earthworks 50% released after substantial completion.
INSERT INTO cost.retention_money (
    id, created_at, updated_at, version,
    project_id, contract_id, retained_amount, retention_percentage,
    released_amount, release_date, status
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, c.id, r.retained, r.pct, r.released, r.release_date, r.status
FROM (VALUES
    ('BNK-MAIN-2024-001',   2500000.00, 5.0,        0.00, NULL,                   'RETAINED'),
    ('BNK-SUB-EARTH-2024',   400000.00, 5.0,   200000.00, DATE '2025-12-15',     'PARTIALLY_RELEASED'),
    ('BNK-SUB-PAVE-2025',    600000.00, 5.0,        0.00, NULL,                   'RETAINED'),
    ('BNK-SUB-BRIDGE-2025',  750000.00, 5.0,        0.00, NULL,                   'RETAINED')
) AS r(contract_number, retained, pct, released, release_date, status)
JOIN contract.contracts c ON c.contract_number = r.contract_number
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
WHERE NOT EXISTS (
    SELECT 1 FROM cost.retention_money x
    WHERE x.contract_id = c.id
);

-- ─────────────────────────── DPR Estimates ───────────────────────────
-- Detailed Project Report estimates by cost category, anchored to representative WBS nodes.
-- Categories: CIVIL (earthworks+pavement), STRUCTURAL (bridges), ELECTRICAL (light/ITS),
-- ESCALATION (price-rise reserve), CONTINGENCY (general).
INSERT INTO cost.dpr_estimates (
    id, created_at, updated_at, version,
    project_id, wbs_node_id, cost_category,
    estimated_amount, revised_amount, remarks
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id,
       (SELECT id FROM project.wbs_nodes WHERE project_id = p.id AND code = e.wbs_code),
       e.category, e.estimated, e.revised, e.remarks
FROM (VALUES
    ('WBS-2',  'CIVIL',
        18500000.00, 18680000.00,
        'Earthworks + pavement civil scope including subgrade, GSB, WMM, DBM and BC. Revised after VO-001.'),
    ('WBS-8',  'STRUCTURAL',
        15000000.00, 15320000.00,
        'Three Wadi crossings (Mistal, Al Hattat, Far) — pile foundations + PSC girder superstructure.'),
    ('WBS-7',  'ELECTRICAL',
         4200000.00,  4520000.00,
        'Street lighting, ITS gantries, OETC HT crossing relocation (mirrors VO-002 underground scope).'),
    ('WBS-1',  'ESCALATION',
         2400000.00,  2400000.00,
        'Price escalation reserve — 4% of base civil scope for 24-month execution window.'),
    ('WBS-1',  'CONTINGENCY',
         1500000.00,  1380000.00,
        'General project contingency (3% of contract value); reduced after Q1-2026 risk burn-down.')
) AS e(wbs_code, category, estimated, revised, remarks)
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
WHERE NOT EXISTS (
    SELECT 1 FROM cost.dpr_estimates existing
    WHERE  existing.project_id = p.id AND existing.cost_category = e.category
);

-- ─────────────────────────── Monthly BOQ Projections ───────────────────────────
-- 5 high-value BOQ items × 6 forward months = 30 rows. Quantities and amounts are illustrative
-- forward plans aligned with the data-date window 2026-05 → 2026-10. Unique constraint on
-- (project_id, boq_item_no, year_month) provides idempotency.
INSERT INTO project.monthly_boq_projections (
    id, created_at, updated_at, version,
    project_id, boq_item_no, year_month, planned_qty, planned_amount, remarks
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, m.boq_item_no, m.ym, m.qty, m.amount, m.remarks
FROM (VALUES
    -- Item 15.17.6(ix)b  — Lighting column foundation
    ('15.17.6(ix)b',  '2026-05', 600.000,  80520.00, 'May 2026 plan: 600 nos. lighting column foundations'),
    ('15.17.6(ix)b',  '2026-06', 800.000, 107360.00, 'June plan: 800 nos. — Barka end pickup'),
    ('15.17.6(ix)b',  '2026-07', 900.000, 120780.00, 'July plan: peak month — 900 nos.'),
    ('15.17.6(ix)b',  '2026-08', 800.000, 107360.00, 'August plan: 800 nos.'),
    ('15.17.6(ix)b',  '2026-09', 700.000,  93940.00, 'September plan: 700 nos.'),
    ('15.17.6(ix)b',  '2026-10', 500.000,  67100.00, 'October plan: 500 nos. — closure'),

    -- Item 8.3.6(i)c  — Manhole chambers
    ('8.3.6(i)c',     '2026-05', 600.000,  79932.00, 'Drainage MH chambers — Wadi Mistal stretch'),
    ('8.3.6(i)c',     '2026-06', 800.000, 106576.00, 'June — Wadi Al Hattat stretch'),
    ('8.3.6(i)c',     '2026-07', 900.000, 119898.00, 'July peak — Wadi Far stretch'),
    ('8.3.6(i)c',     '2026-08', 800.000, 106576.00, 'August — Nakhal end'),
    ('8.3.6(i)c',     '2026-09', 700.000,  93254.00, 'September — closure works'),
    ('8.3.6(i)c',     '2026-10', 519.000,  69150.00, 'October — final commissioning'),

    -- Item 1.7.6(iv) — Prime coat
    ('1.7.6(iv)',     '2026-05', 600.000,  80172.00, 'Prime coat — Barka end completion'),
    ('1.7.6(iv)',     '2026-06', 700.000,  93534.00, 'Prime coat — full corridor coverage'),
    ('1.7.6(iv)',     '2026-07', 800.000, 106896.00, 'Peak month'),
    ('1.7.6(iv)',     '2026-08', 700.000,  93534.00, 'Pre-DBM ramp-down'),
    ('1.7.6(iv)',     '2026-09', 405.000,  54108.00, 'September final touch-ups'),
    ('1.7.6(iv)',     '2026-10', 100.000,  13362.00, 'Touch-up only'),

    -- Item 8.4.6(ii)c  — UPVC drainage pipes
    ('8.4.6(ii)c',    '2026-05', 600.000,  79911.00, 'UPVC pipes — underpass drainage W. Al Hattat'),
    ('8.4.6(ii)c',    '2026-06', 700.000,  93229.50, 'June plan'),
    ('8.4.6(ii)c',    '2026-07', 800.000, 106548.00, 'Peak — three underpasses'),
    ('8.4.6(ii)c',    '2026-08', 700.000,  93229.50, 'August plan'),
    ('8.4.6(ii)c',    '2026-09', 700.000,  93229.50, 'September plan'),
    ('8.4.6(ii)c',    '2026-10', 770.000, 102577.00, 'October closure'),

    -- Item 8.2.6(i)d  — HDPE storm sewer
    ('8.2.6(i)d',     '2026-05', 500.000,  66643.00, 'HDPE storm sewer — first carriageway'),
    ('8.2.6(i)d',     '2026-06', 700.000,  93300.00, 'Second carriageway start'),
    ('8.2.6(i)d',     '2026-07', 800.000, 106628.00, 'Peak'),
    ('8.2.6(i)d',     '2026-08', 700.000,  93300.00, 'Plan'),
    ('8.2.6(i)d',     '2026-09', 700.000,  93300.00, 'Plan'),
    ('8.2.6(i)d',     '2026-10', 793.000, 105642.00, 'Final closure')
) AS m(boq_item_no, ym, qty, amount, remarks)
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
ON CONFLICT (project_id, boq_item_no, year_month) DO NOTHING;

-- ─────────────────────────── Corridor Codes ───────────────────────────
-- Single corridor identifier for the project. UNIQUE on project_id and on generated_code.
INSERT INTO project.corridor_codes (
    id, created_at, updated_at, version,
    project_id, corridor_prefix, zone_code, node_code, generated_code
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, 'BNK', 'OHA-MUS-BAT', 'BARKA-NAKHAL',
       'BNK-OHA-MUS-BAT-BARKA-NAKHAL'
FROM (SELECT id FROM project.projects WHERE code = '6155') AS p
ON CONFLICT (project_id) DO NOTHING;
