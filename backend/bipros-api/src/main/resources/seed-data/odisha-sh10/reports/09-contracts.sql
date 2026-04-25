-- 09-contracts.sql
-- Main EPC contract (Megha Engineering & Infrastructures Ltd) + a specialist
-- bitumen sub-contract (IndianOil Corporation Ltd ex-Paradip refinery).
-- These drive:
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
    status, contract_type, currency,
    wbs_package_code, package_description,
    spi, cpi, physical_progress_ai,
    cumulative_ra_bills_crores, vo_numbers_issued, vo_value_crores,
    performance_score, kpi_refreshed_at
) VALUES
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001'),
    'SH10-EPC-2024-001', 'OWD/PIU-BBSR/SH10/LOA/2024-082',
    'Megha Engineering & Infrastructures Ltd', 'MEIL-GSTN-21AAACM7654L1Z3',
    4525000000.00, DATE '2024-07-15', DATE '2024-08-15', DATE '2026-12-31',
    24, 0.10,
    'ACTIVE_AT_RISK', 'EPC_LUMP_SUM_FIDIC_YELLOW', 'INR',
    'SH10-MAIN', 'SH-10 Bhubaneswar-Cuttack 4-laning & Strengthening — 28 km main carriageway works (Ch 0+000 to Ch 28+000) including 2 minor bridges and 1 ROB at Cuttack approach.',
    0.943, 0.948, 75.01,
    358.20, 2, 3.20,
    76.50, CURRENT_TIMESTAMP
),
(
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001'),
    'SH10-BIT-SUB-2025-001', 'OWD/PIU-BBSR/SH10/LOA-BIT/2025-014',
    'IndianOil Corporation Ltd', 'IOC-GSTN-21AAACI1681G1ZF',
    130000000.00, DATE '2025-04-10', DATE '2025-05-01', DATE '2026-09-30',
    12, 0.05,
    'ACTIVE', 'ITEM_RATE_FIDIC_RED', 'INR',
    'SH10-BIT', 'Supply of VG-30 grade bitumen ex-IOC Paradip refinery including transit insurance and unloading at MEIL hot mix plant site at Khurda.',
    0.92, 0.97, 65.00,
    7.92, 0, 0.00,
    84.00, CURRENT_TIMESTAMP
);
