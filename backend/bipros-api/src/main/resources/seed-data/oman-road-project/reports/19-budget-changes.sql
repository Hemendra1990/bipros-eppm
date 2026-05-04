-- BNK reports/19-budget-changes.sql — 5 budget change requests across the Barka–Nakhal project.
--
--   BCR-2025-001  ADDITION  +180,000 OMR   APPROVED  Pile-cap depth at Wadi Al Hattat (mirrors VO-001)
--   BCR-2025-002  TRANSFER   320,000 OMR   APPROVED  Re-allocation from preliminaries float to drainage
--   BCR-2026-001  ADDITION  +320,000 OMR   PENDING   OETC HT crossing underground (mirrors VO-002)
--   BCR-2026-002  REDUCTION  -85,000 OMR   APPROVED  Decorative median plantation deferred
--   BCR-2026-003  TRANSFER    60,000 OMR   REJECTED  Proposed shift bridge contingency → finishing
--
-- change_type ∈ {ADDITION, REDUCTION, TRANSFER}, status ∈ {PENDING, APPROVED, REJECTED}.
-- requested_by / decided_by are foreign keys to public.users (admin user used for all entries).
-- TRANSFER rows populate both from_wbs_node_id and to_wbs_node_id; ADDITION/REDUCTION
-- populate only to_wbs_node_id.

INSERT INTO cost.budget_change_logs (
    id, created_at, updated_at, version,
    project_id, change_type, amount, reason, status,
    requested_at, requested_by,
    decided_at, decided_by,
    from_wbs_node_id, to_wbs_node_id
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, b.change_type, b.amount, b.reason, b.status,
       b.requested_at, u.id,
       b.decided_at, u.id,
       (SELECT id FROM project.wbs_nodes WHERE project_id = p.id AND code = b.from_wbs),
       (SELECT id FROM project.wbs_nodes WHERE project_id = p.id AND code = b.to_wbs)
FROM (VALUES
    ('ADDITION',   180000.00,
     'Wadi Al Hattat Pier P3 — additional pile-cap depth driven by N<6 strata at 6m. Mirrors approved VO-001.',
     'APPROVED',
     TIMESTAMP '2025-08-22 09:00:00+04:00', TIMESTAMP '2025-08-30 14:15:00+04:00',
     NULL,        'WBS-9'),

    ('TRANSFER',   320000.00,
     'Reallocate unspent preliminaries float to drainage works to cover monsoon culvert protection upgrades.',
     'APPROVED',
     TIMESTAMP '2025-11-04 11:20:00+04:00', TIMESTAMP '2025-11-19 16:00:00+04:00',
     'WBS-1',     'WBS-3'),

    ('ADDITION',   320000.00,
     'OETC 132 kV HT crossing underground relocation at Ch 24+500 — pending Diwan approval, mirrors VO-002.',
     'PENDING',
     TIMESTAMP '2026-01-26 10:00:00+04:00', NULL,
     NULL,        'WBS-7'),

    ('REDUCTION',  -85000.00,
     'Decorative median plantation enhancement deferred — reduce scope until next financial year.',
     'APPROVED',
     TIMESTAMP '2026-04-12 13:30:00+04:00', TIMESTAMP '2026-04-26 09:45:00+04:00',
     NULL,        'WBS-9'),

    ('TRANSFER',    60000.00,
     'Proposed shift of bridge contingency reserve toward finishing/signage works — rejected pending audit.',
     'REJECTED',
     TIMESTAMP '2026-04-18 15:00:00+04:00', TIMESTAMP '2026-04-29 11:30:00+04:00',
     'WBS-8',     'WBS-9')
) AS b(change_type, amount, reason, status,
       requested_at, decided_at,
       from_wbs, to_wbs)
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
CROSS JOIN (SELECT id FROM public.users WHERE username = 'admin') AS u
WHERE NOT EXISTS (
    SELECT 1 FROM cost.budget_change_logs existing
    WHERE  existing.project_id = p.id
      AND  existing.reason     = b.reason
);
