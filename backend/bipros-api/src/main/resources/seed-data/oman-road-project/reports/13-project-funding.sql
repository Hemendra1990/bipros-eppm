-- BNK reports/13-project-funding.sql — MoTC Capital Budget funding source for 6155.
--
-- Plan §4.34: 1 funding source (MOTC Capital Budget 2024–2026) covering the
-- total of all contract values for the Barka–Nakhal project.
--
-- Total contract value (Main + 3 subs) = 50M + 8M + 12M + 15M = 85M OMR.
-- BUT BAC for the project is ~6.5M OMR (from §07).  We use the plan's
-- "total contract value" interpretation: allocated_amount = sum of contracts =
-- 85,000,000 OMR.  remaining_amount = total - 70% disbursed = 30%.
--
-- Note: FundingSource entity stores allocated_amount, total_amount,
-- remaining_amount.  Plan's "receivedAmount = ~70%" is interpreted as
-- "70% of the allocation has been disbursed", so remaining_amount = 30%.

INSERT INTO cost.funding_sources (
    id, created_at, updated_at, version,
    code, name, description,
    total_amount, allocated_amount, remaining_amount
)
VALUES (
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'FS-MOTC-BNK-CAPITAL',
    'MoTC Capital Budget — Barka–Nakhal Dualization 2024–2026',
    'MoTC capital budget allocation, National Roads Programme. Sanction MOTC/CAP/BNK/2024-001 dated 2024-07-15. Covers 41 km Barka-Nakhal dualization.',
    85000000.000, 85000000.000, 25500000.000
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO cost.project_funding (
    id, created_at, updated_at, version,
    project_id, funding_source_id, wbs_node_id, allocated_amount
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, fs.id, NULL, 85000000.000
FROM cost.funding_sources fs
JOIN project.projects p ON p.code = '6155'
WHERE fs.code = 'FS-MOTC-BNK-CAPITAL'
  AND NOT EXISTS (
        SELECT 1 FROM cost.project_funding pf
        WHERE  pf.project_id        = p.id
          AND  pf.funding_source_id = fs.id
          AND  pf.wbs_node_id IS NULL
      );
