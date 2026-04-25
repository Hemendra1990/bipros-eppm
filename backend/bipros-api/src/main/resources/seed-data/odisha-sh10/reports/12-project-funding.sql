-- 12-project-funding.sql
-- Funding sources + project_funding rows.  Drives the PFMS pass on the
-- Compliance panel (Compliance endpoint counts project_funding rows for
-- the project).  Four funding streams: OWD State budget + NABARD RIDF loan
-- + Central Road Fund cess allocation + OWD contingency reserve.
-- Total ₹4,525 Cr = exact BAC; allocated ₹4,325 M (remaining ₹200M
-- earmarked for contingency reallocation — realistic tranche staging).

INSERT INTO cost.funding_sources (
    id, created_at, updated_at, version,
    code, name, description,
    total_amount, allocated_amount, remaining_amount
) VALUES
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'FS-OWD-SH10-OWD',
    'OWD State Budget — SH-10 BBSR-CTC Widening',
    'OWD State Plan budget allocation under Odisha State Highway Programme, sanction order OWD/SH10/SP/2024-082 dated 2024-06-15.',
     3000000000.00,  2800000000.00,   200000000.00
),
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'FS-OWD-SH10-NABARD',
    'NABARD RIDF Tranche XXIX — SH-10 Loan',
    'NABARD Rural Infrastructure Development Fund Tranche XXIX loan covenant for SH-10 Bhubaneswar-Cuttack widening, agreement RIDF-XXIX/OD-04/2024 signed 2024-08-12.',
     1000000000.00,   900000000.00,   100000000.00
),
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'FS-OWD-SH10-CRF',
    'Central Road Fund Cess Allocation',
    'MoRTH Central Road Fund cess allocation per CRF Act 2000 for state highway cess share.',
      400000000.00,   400000000.00,           0.00
),
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'FS-OWD-SH10-CONT',
    'OWD Contingency Reserve',
    'OWD-borne contingency reserve for VOs, utility shifting, and force majeure events under contract Clause 13.',
      225000000.00,   225000000.00,           0.00
);

INSERT INTO cost.project_funding (
    id, created_at, updated_at, version,
    project_id, funding_source_id, wbs_node_id, allocated_amount
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.id, fs.id, NULL, a.allocated
FROM (VALUES
    ('FS-OWD-SH10-OWD',    2800000000.00),
    ('FS-OWD-SH10-NABARD',  900000000.00),
    ('FS-OWD-SH10-CRF',     400000000.00),
    ('FS-OWD-SH10-CONT',    225000000.00)
) AS a(source_code, allocated)
JOIN cost.funding_sources fs ON fs.code = a.source_code
CROSS JOIN (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001') AS p;
