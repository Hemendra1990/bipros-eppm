-- BNK reports/10-contracts.sql — Main works contract + 3 subcontracts for 6155.
--
-- Plan §4.31:
--   BNK-MAIN-2024-001  Main Civil Works Contract  50,000,000 OMR  ACTIVE
--   BNK-SUB-EARTH-2024 Earthworks Subcontract      8,000,000 OMR  ACTIVE
--   BNK-SUB-PAVE-2025  Pavement & Bituminous      12,000,000 OMR  ACTIVE
--   BNK-SUB-BRIDGE-2025 Bridges & Structures      15,000,000 OMR  ACTIVE
--
-- ContractStatus enum has ACTIVE (the plan says "EXECUTION" — same thing
-- in the bipros-eppm enum vocabulary).  ContractType picks from the
-- FIDIC variants for the main contract and ITEM_RATE for subs.
-- Currency = OMR (3-decimal precision).

-- ContractStatus / ContractType are stored as VARCHAR via @Enumerated(STRING),
-- so we pass them as plain strings.
INSERT INTO contract.contracts (
    id, created_at, updated_at, version,
    project_id, contract_number, loa_number,
    contractor_name, contractor_code,
    contract_value, loa_date, start_date, completion_date,
    dlp_months, ld_rate,
    status, contract_type, currency,
    wbs_package_code, package_description,
    spi, cpi, physical_progress_ai,
    cumulative_ra_bills_crores, vo_numbers_issued, vo_value_crores,
    performance_score, kpi_refreshed_at
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       (SELECT id FROM project.projects WHERE code = '6155'),
       c.contract_number, c.loa_number,
       c.contractor, c.contractor_code,
       c.value, c.loa_date, c.start_date, c.completion_date,
       c.dlp_months, c.ld_rate,
       c.status, c.contract_type, 'OMR',
       c.wbs_pkg, c.pkg_desc,
       c.spi, c.cpi, c.progress_ai,
       c.cum_ra, c.vo_issued, c.vo_value,
       c.score, CURRENT_TIMESTAMP
FROM (VALUES
    ('BNK-MAIN-2024-001', 'MOTC/BNK/LOA/2024-001',
     'Galfar Engineering & Contracting SAOG', 'GALFAR-CR-1054678',
      50000000.000, DATE '2024-08-15', DATE '2024-09-01', DATE '2026-08-31',
      24, 0.10,
     'ACTIVE', 'EPC_LUMP_SUM_FIDIC_YELLOW',
     'BNK-MAIN',
     'Main Civil Works Contract — Dualization of Barka–Nakhal Road (41 km, 4-lane divided carriageway, 3 Wadi crossings, OETC HT crossing relocations).',
     0.926, 0.950, 67.69, 21.50, 1, 1.80, 78.50),

    ('BNK-SUB-EARTH-2024', 'MOTC/BNK/SUB-EARTH/2024-002',
     'Al Turki Enterprises LLC', 'ALTURKI-CR-2107845',
       8000000.000, DATE '2024-09-12', DATE '2024-10-01', DATE '2025-09-30',
      12, 0.05,
     'ACTIVE', 'ITEM_RATE_FIDIC_RED',
     'BNK-SUB-EARTH',
     'Earthworks Subcontract — mass excavation, embankment construction, and subgrade preparation across 41 km corridor including soft strata treatment at Wadi crossings.',
     0.985, 1.020, 92.00, 6.80, 0, 0.00, 84.00),

    ('BNK-SUB-PAVE-2025', 'MOTC/BNK/SUB-PAVE/2025-003',
     'Strabag Oman LLC', 'STRABAG-CR-3214567',
      12000000.000, DATE '2025-04-20', DATE '2025-05-15', DATE '2026-08-15',
      18, 0.07,
     'ACTIVE', 'ITEM_RATE_FIDIC_RED',
     'BNK-SUB-PAVE',
     'Pavement & Bituminous Works — GSB, WMM, DBM and BC layer placement, surface dressing, and pavement marking across full 41 km dualised carriageway.',
     0.910, 0.945, 60.00, 7.20, 0, 0.00, 76.00),

    ('BNK-SUB-BRIDGE-2025', 'MOTC/BNK/SUB-BRIDGE/2025-004',
     'L&T Construction Oman SAOC', 'LNT-CR-4087512',
      15000000.000, DATE '2025-01-10', DATE '2025-02-01', DATE '2026-06-30',
      24, 0.10,
     'ACTIVE', 'EPC_LUMP_SUM_FIDIC_YELLOW',
     'BNK-SUB-BRIDGE',
     'Bridges & Structures Subcontract — three Wadi crossing bridges (Wadi Mistal, Wadi Al Hattat, Wadi Far) including pile foundations, pier/abutment substructure, and PSC girder superstructure.',
     0.890, 0.920, 55.00, 7.50, 1, 0.18, 72.00)
) AS c(contract_number, loa_number, contractor, contractor_code,
       value, loa_date, start_date, completion_date,
       dlp_months, ld_rate,
       status, contract_type,
       wbs_pkg, pkg_desc,
       spi, cpi, progress_ai,
       cum_ra, vo_issued, vo_value,
       score)
ON CONFLICT (contract_number) DO NOTHING;
