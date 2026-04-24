-- 12-project-funding.sql
-- Funding source + project_funding row.  Drives the PFMS pass on the
-- Compliance panel (Compliance endpoint counts project_funding rows for
-- the project).  Two funding streams: Central-sector NHAI budgetary
-- support + JICA ODA loan tranche.  Total ₹500 Cr (allocated more than
-- ₹485 Cr BAC to cover contingency and VOs).

INSERT INTO cost.funding_sources (
    id, created_at, updated_at, version,
    code, name, description,
    total_amount, allocated_amount, remaining_amount
) VALUES
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'FS-NHAI-NH48-CENTRAL',
    'NHAI Budgetary Support — NH-48 Widening (Rajasthan)',
    'Central Government NHAI budget allocation under the Bharatmala Pariyojana Phase-I for NH-48 Rajasthan stretch, sanction order NHAI/CO/BP-I/NH48-RJ/2024-148 dated 2024-12-20.',
     3500000000.00,  3200000000.00,   300000000.00
),
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'FS-NHAI-NH48-JICA',
    'JICA ODA Loan — NH-48 Corridor Facility Tranche',
    'Japan International Cooperation Agency ODA loan covenant covering co-financing for NH-48 Rajasthan widening (Loan Agreement ID-R2-247, signed 2024-09-12).',
     1500000000.00,  1300000000.00,   200000000.00
);

INSERT INTO cost.project_funding (
    id, created_at, updated_at, version,
    project_id, funding_source_id, wbs_node_id, allocated_amount
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.id, fs.id, NULL, a.allocated
FROM (VALUES
    ('FS-NHAI-NH48-CENTRAL', 3200000000.00),
    ('FS-NHAI-NH48-JICA',    1300000000.00)
) AS a(source_code, allocated)
JOIN cost.funding_sources fs ON fs.code = a.source_code
CROSS JOIN (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001') AS p;
