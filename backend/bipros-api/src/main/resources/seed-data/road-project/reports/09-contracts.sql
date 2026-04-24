-- 09-contracts.sql
-- Main EPC contract (ABC Infracon) + a specialist bitumen sub-contract
-- (XYZ Bitumen).  These drive:
--   * Compliance: GSTN (distinct contractor_code count) and GeM orders
--   * Bills & VOs tab: RA bills and VOs reference the main contract
--   * Status tab: CPI/SPI pulled from contract-level denormalised KPIs.
-- Values in rupees.

INSERT INTO contract.contracts (
    id, created_at, updated_at, version,
    project_id, contract_number, loa_number,
    contractor_name, contractor_code,
    contract_value, loa_date, start_date, completion_date,
    dlp_months, ld_rate,
    status, contract_type,
    wbs_package_code, package_description,
    spi, cpi, physical_progress_ai,
    cumulative_ra_bills_crores, vo_numbers_issued, vo_value_crores,
    performance_score, kpi_refreshed_at
) VALUES
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001'),
    'NH48-MAIN-2024-001', 'NHAI/RJ/NH48/LOA/2024-148',
    'ABC Infracon Pvt Ltd', 'ABC-GSTN-08AAACA1234L1Z8',
    4850000000.00, DATE '2024-11-28', DATE '2024-12-15', DATE '2026-12-31',
    24, 0.10,
    'ACTIVE_AT_RISK', 'EPC_LUMP_SUM_FIDIC_YELLOW',
    'NH48-MAIN', 'NH-48 Rajasthan 4-lane widening — 20 km main carriageway works (Ch 145+000 to Ch 165+000).',
    0.901, 0.947, 88.25,
    159.00, 2, 15.70,
    78.50, CURRENT_TIMESTAMP
),
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001'),
    'NH48-BIT-SUB-2025-001', 'NHAI/RJ/NH48/LOA-BIT/2025-022',
    'XYZ Bitumen Supplies Ltd', 'XYZ-GSTN-08AABCX9876P1Z4',
    680000000.00, DATE '2025-05-20', DATE '2025-06-01', DATE '2026-06-30',
    12, 0.05,
    'ACTIVE', 'ITEM_RATE_FIDIC_RED',
    'NH48-BIT', 'Supply of VG-30 bitumen ex-IOCL Panipat including transit insurance and unloading at DBM/BC mixing plant site.',
    0.92, 0.98, 48.00,
    24.80, 0, 0.00,
    82.00, CURRENT_TIMESTAMP
);
