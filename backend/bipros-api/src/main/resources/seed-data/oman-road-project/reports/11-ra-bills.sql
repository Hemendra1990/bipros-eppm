-- BNK reports/11-ra-bills.sql — 9 monthly RA bills + ~70 RaBillItem rows.
--
-- Plan §4.33: 9 monthly Running Account bills against the main contract
-- (BNK-MAIN-2024-001). Status mix:
--   4 PAID       — RA-001 .. RA-004 (Sep 2025 → Dec 2025 paid)
--   4 APPROVED   — RA-005 .. RA-008 (Jan → Apr 2026, awaiting MoTC payment)
--   1 SUBMITTED  — RA-009 (newest, under PMC certification)
--
-- All amounts in OMR.  Deductions follow Oman MoTC standard:
--   5% retention, 5% mob-advance recovery (against initial 10% mob advance).
--   GST/TDS columns are kept in the schema but set to 0 (not applicable in
--   Oman) to keep the data shape consistent with the NHAI template.

-- ── RA Bills ──────────────────────────────────────────────────────────────
INSERT INTO cost.ra_bills (
    id, created_at, updated_at, version,
    project_id, contract_id, wbs_package_code, bill_number,
    bill_period_from, bill_period_to,
    gross_amount, retention_5_pct, tds_2_pct, gst_18_pct, mob_advance_recovery,
    net_amount, cumulative_amount,
    ai_satellite_percent, contractor_claimed_percent, satellite_gate, satellite_gate_variance,
    status,
    submitted_date, certified_date, approved_date, paid_date, payment_date,
    certified_by, approved_by,
    pfms_dpa_ref, remarks
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       c.project_id, c.id, 'BNK-MAIN', b.bill_number,
       b.period_from, b.period_to,
       b.gross, b.retention, 0.000, 0.000, b.mob_recovery,
       b.net, b.cumulative,
       b.ai_pct, b.claimed_pct, b.gate, b.variance,
       b.status,
       b.submitted, b.certified, b.approved, b.paid, b.paid,
       b.certified_by, b.approved_by,
       b.dpa_ref, b.remarks
FROM (VALUES
    ('BNK-RA-001', DATE '2024-09-01', DATE '2024-12-31',
        420000.000, 21000.000, 21000.000, 357000.000, 600000.000,
        18.00, 19.00, 'PASS', 1.00, 'PAID',
        DATE '2025-01-12', DATE '2025-01-30', DATE '2025-02-10', DATE '2025-02-25',
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        'MOTC-DPA/BNK/2025-001/021', 'First RA — mobilisation + initial earthwork south end (Ch 0+000–8+500).'),
    ('BNK-RA-002', DATE '2025-01-01', DATE '2025-03-31',
        680000.000, 34000.000, 34000.000, 578000.000,  900000.000,
        28.00, 29.50, 'PASS', 1.50, 'PAID',
        DATE '2025-04-08', DATE '2025-04-25', DATE '2025-05-08', DATE '2025-05-22',
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        'MOTC-DPA/BNK/2025-002/058', 'Earthwork mid-section + Wadi Mistal pier P1 substructure.'),
    ('BNK-RA-003', DATE '2025-04-01', DATE '2025-06-30',
        780000.000, 39000.000, 39000.000, 663000.000,  600000.000,
        38.50, 40.00, 'PASS', 1.50, 'PAID',
        DATE '2025-07-09', DATE '2025-07-26', DATE '2025-08-08', DATE '2025-08-22',
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        'MOTC-DPA/BNK/2025-003/094', 'Earthwork final + GSB partial + Wadi Al Hattat substructure.'),
    ('BNK-RA-004', DATE '2025-07-01', DATE '2025-09-30',
        820000.000, 41000.000, 41000.000, 697000.000,  600000.000,
        50.50, 52.00, 'PASS', 1.50, 'PAID',
        DATE '2025-10-10', DATE '2025-10-28', DATE '2025-11-10', DATE '2025-11-24',
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        'MOTC-DPA/BNK/2025-004/138', 'GSB/WMM completion + bridge substructure handover.'),
    ('BNK-RA-005', DATE '2025-10-01', DATE '2025-11-30',
        540000.000, 27000.000, 27000.000, 459000.000,  300000.000,
        58.00, 59.00, 'PASS', 1.00, 'APPROVED',
        DATE '2025-12-08', DATE '2025-12-22', DATE '2026-01-05', NULL,
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        NULL, 'DBM partial + bridge superstructure launching at Wadi Mistal — payment pending MOTC DPA release.'),
    ('BNK-RA-006', DATE '2025-12-01', DATE '2026-01-31',
        470000.000, 23500.000, 23500.000, 399500.000,  200000.000,
        62.50, 63.50, 'PASS', 1.00, 'APPROVED',
        DATE '2026-02-09', DATE '2026-02-22', DATE '2026-03-08', NULL,
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        NULL, 'BC layer Phase 1 + bridge superstructure deck slab — payment scheduled FY26 Q3.'),
    ('BNK-RA-007', DATE '2026-02-01', DATE '2026-02-28',
        390000.000, 19500.000, 19500.000, 331500.000,  150000.000,
        65.00, 65.50, 'PASS', 0.50, 'APPROVED',
        DATE '2026-03-09', DATE '2026-03-22', DATE '2026-04-04', NULL,
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        NULL, 'Drainage cross-culverts + road furniture mobilisation.'),
    ('BNK-RA-008', DATE '2026-03-01', DATE '2026-03-31',
        320000.000, 16000.000, 16000.000, 272000.000,  100000.000,
        67.50, 68.00, 'PASS', 0.50, 'APPROVED',
        DATE '2026-04-08', DATE '2026-04-20', DATE '2026-04-25', NULL,
        'Eng. Said Al-Habsi, PMC Resident Engineer', 'Eng. Khalid Al-Saadi, MoTC Project Director',
        NULL, 'BC layer Phase 2 + signage deployment + median plantation start.'),
    ('BNK-RA-009', DATE '2026-04-01', DATE '2026-04-29',
        290000.000, 14500.000, 14500.000, 246500.000,   75000.000,
        69.00, 71.00, 'HOLD_VARIANCE', 2.00, 'SUBMITTED',
        DATE '2026-05-02', NULL, NULL, NULL,
        NULL, NULL,
        NULL, 'OETC HT crossing relocation Phase 1 + final BC stretch — under PMC certification review.')
) AS b(bill_number, period_from, period_to,
       gross, retention, mob_recovery, net, cumulative,
       ai_pct, claimed_pct, gate, variance, status,
       submitted, certified, approved, paid,
       certified_by, approved_by, dpa_ref, remarks)
CROSS JOIN (
    SELECT c.id, c.project_id FROM contract.contracts c
    WHERE c.contract_number = 'BNK-MAIN-2024-001'
) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM cost.ra_bills existing
    WHERE  existing.contract_id = c.id
      AND  existing.bill_number = b.bill_number
);

-- Pre-compute net (gross - retention - mob_recovery) and cumulative once;
-- the VALUES tuple gives us the headline numbers but we restated them
-- explicitly so a reviewer can audit each row.  The cumulative column is
-- patched separately below from the running total of net amounts in DB.
UPDATE cost.ra_bills SET
    net_amount = gross_amount - COALESCE(retention_5_pct,0) - COALESCE(mob_advance_recovery,0)
WHERE wbs_package_code = 'BNK-MAIN'
  AND project_id = (SELECT id FROM project.projects WHERE code = '6155')
  AND ABS(net_amount - (gross_amount - COALESCE(retention_5_pct,0) - COALESCE(mob_advance_recovery,0))) > 0.001;

WITH ordered AS (
    SELECT id,
           SUM(net_amount) OVER (PARTITION BY contract_id
                                 ORDER BY bill_period_from
                                 ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_cum
    FROM   cost.ra_bills
    WHERE  wbs_package_code = 'BNK-MAIN'
      AND  project_id = (SELECT id FROM project.projects WHERE code = '6155')
)
UPDATE cost.ra_bills b
SET    cumulative_amount = o.running_cum,
       updated_at        = CURRENT_TIMESTAMP
FROM   ordered o
WHERE  b.id = o.id;

-- ── RA Bill Items ────────────────────────────────────────────────────────
-- ~70 line items spread across the 9 RA bills.  Each bill gets 7–9 items.
-- Item codes mirror the BOQ section taxonomy (BOQ-1.1, BOQ-3.1, …).
INSERT INTO cost.ra_bill_items (
    id, created_at, updated_at, version,
    ra_bill_id, item_code, description, unit, rate,
    previous_quantity, current_quantity, cumulative_quantity, amount
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       b.id, i.item_code, i.description, i.unit, i.rate,
       i.prev_qty, i.curr_qty, i.cum_qty, i.amount
FROM (VALUES
    -- bill_number,         item_code,    description,                                            unit,    rate,        prev_qty, curr_qty, cum_qty,  amount
    ('BNK-RA-001', 'BOQ-1.1',  'Mobilisation — site office, contractor camp, batching plant',     'lump',     50000.000,    0.0,   1.0,   1.0,    50000.000),
    ('BNK-RA-001', 'BOQ-1.2',  'Survey & setting out for 41 km corridor',                          'km',        2500.000,    0.0,  20.0,  20.0,    50000.000),
    ('BNK-RA-001', 'BOQ-2.1',  'Site clearance & vegetation removal',                              'sqm',          0.300,    0.0,150000.0,150000.0, 45000.000),
    ('BNK-RA-001', 'BOQ-3.1',  'Excavation in soft strata',                                        'cum',          1.500,    0.0,80000.0, 80000.0, 120000.000),
    ('BNK-RA-001', 'BOQ-3.2',  'Embankment with approved fill',                                    'cum',          1.200,    0.0,60000.0, 60000.0,  72000.000),
    ('BNK-RA-001', 'BOQ-1.3',  'HSE compliance — first aid, signage, PPE',                         'lump',     25000.000,    0.0,   1.0,   1.0,    25000.000),
    ('BNK-RA-001', 'BOQ-1.4',  'Worker housing certification',                                     'lump',     58000.000,    0.0,   1.0,   1.0,    58000.000),

    ('BNK-RA-002', 'BOQ-3.1',  'Excavation in soft strata (cont.)',                                'cum',          1.500, 80000.0,140000.0,220000.0,210000.000),
    ('BNK-RA-002', 'BOQ-3.2',  'Embankment (cont.)',                                               'cum',          1.200, 60000.0, 95000.0,155000.0,114000.000),
    ('BNK-RA-002', 'BOQ-3.3',  'Subgrade preparation & compaction',                                'sqm',          0.800,    0.0, 95000.0, 95000.0, 76000.000),
    ('BNK-RA-002', 'BOQ-5.1',  'Bored cast-in-situ piles 1200 mm dia × 22 m (Wadi Mistal)',        'm',           90.000,    0.0,  1500.0,  1500.0,135000.000),
    ('BNK-RA-002', 'BOQ-5.2',  'Pier P1 substructure RCC M40',                                     'cum',        180.000,    0.0,   180.0,   180.0, 32400.000),
    ('BNK-RA-002', 'BOQ-1.5',  'Site supervision & contract administration (Q1)',                  'month',     15000.000,    0.0,     3.0,     3.0, 45000.000),
    ('BNK-RA-002', 'BOQ-6.1',  'Cross-culvert RCC pipe 1200 mm dia',                               'm',          120.000,    0.0,   200.0,   200.0, 24000.000),
    ('BNK-RA-002', 'BOQ-3.4',  'Filter/granular drainage layer',                                   'cum',          5.500,    0.0,    8000.0,  8000.0, 44000.000),

    ('BNK-RA-003', 'BOQ-3.3',  'Subgrade preparation (cont.)',                                     'sqm',          0.800, 95000.0,180000.0,275000.0,144000.000),
    ('BNK-RA-003', 'BOQ-4.1',  'GSB layer 200 mm — supply and lay',                                'cum',          7.500,    0.0, 18000.0, 18000.0,135000.000),
    ('BNK-RA-003', 'BOQ-5.3',  'Pier P2/P3 substructure RCC M40',                                  'cum',        180.000,    0.0,   850.0,   850.0,153000.000),
    ('BNK-RA-003', 'BOQ-5.4',  'Pier cap & bearing pedestals',                                     'cum',        220.000,    0.0,   180.0,   180.0, 39600.000),
    ('BNK-RA-003', 'BOQ-1.5',  'Site supervision (Q2)',                                            'month',     15000.000,    3.0,     6.0,     6.0, 45000.000),
    ('BNK-RA-003', 'BOQ-6.2',  'Catch-water drains stone masonry',                                 'm',           45.000,    0.0,  4000.0,  4000.0,180000.000),
    ('BNK-RA-003', 'BOQ-3.5',  'Soft strata treatment at Wadi crossings',                          'cum',         12.000,    0.0,  4000.0,  4000.0, 48000.000),

    ('BNK-RA-004', 'BOQ-4.1',  'GSB layer (cont.)',                                                'cum',          7.500, 18000.0, 30000.0, 48000.0, 90000.000),
    ('BNK-RA-004', 'BOQ-4.2',  'WMM layer 250 mm — supply and lay',                                'cum',          9.500,    0.0, 32000.0, 32000.0,304000.000),
    ('BNK-RA-004', 'BOQ-5.5',  'PSC girder casting & launching (Wadi Mistal)',                     'each',     45000.000,    0.0,     6.0,     6.0,270000.000),
    ('BNK-RA-004', 'BOQ-1.5',  'Site supervision (Q3)',                                            'month',     15000.000,    6.0,     9.0,     9.0, 45000.000),
    ('BNK-RA-004', 'BOQ-6.3',  'RCC box culvert 3 × 3 m',                                          'm',          850.000,    0.0,    50.0,    50.0, 42500.000),
    ('BNK-RA-004', 'BOQ-3.4',  'Filter/granular drainage (cont.)',                                 'cum',          5.500,  8000.0, 14000.0, 14000.0, 33000.000),
    ('BNK-RA-004', 'BOQ-2.2',  'Existing road demolition (south end)',                             'sqm',          1.000,    0.0, 35000.0, 35000.0, 35000.000),

    ('BNK-RA-005', 'BOQ-4.2',  'WMM layer (cont.)',                                                'cum',          9.500, 32000.0, 48000.0, 80000.0,152000.000),
    ('BNK-RA-005', 'BOQ-4.3',  'DBM 50 mm Phase 1',                                                'cum',         18.000,    0.0,  9000.0,  9000.0,162000.000),
    ('BNK-RA-005', 'BOQ-5.6',  'Bridge deck slab RCC M40',                                         'cum',        220.000,    0.0,   400.0,   400.0, 88000.000),
    ('BNK-RA-005', 'BOQ-5.7',  'Approach slab & expansion joints',                                 'cum',        260.000,    0.0,   180.0,   180.0, 46800.000),
    ('BNK-RA-005', 'BOQ-1.5',  'Site supervision (Q4)',                                            'month',     15000.000,    9.0,    12.0,    12.0, 45000.000),
    ('BNK-RA-005', 'BOQ-6.4',  'Median drainage chute',                                            'm',           28.000,    0.0,  1500.0,  1500.0, 42000.000),
    ('BNK-RA-005', 'BOQ-3.5',  'Soft strata treatment (cont.)',                                    'cum',         12.000,  4000.0,  4500.0,  4500.0,  6000.000),

    ('BNK-RA-006', 'BOQ-4.3',  'DBM 50 mm Phase 1 (cont.)',                                        'cum',         18.000,  9000.0, 17000.0, 17000.0,144000.000),
    ('BNK-RA-006', 'BOQ-4.4',  'BC 40 mm wearing course Phase 1',                                  'cum',         32.000,    0.0,  4500.0,  4500.0,144000.000),
    ('BNK-RA-006', 'BOQ-5.6',  'Bridge deck slab (cont.)',                                         'cum',        220.000,   400.0,   620.0,   620.0, 48400.000),
    ('BNK-RA-006', 'BOQ-7.1',  'Crash barrier W-beam galvanised',                                  'm',           38.000,    0.0,  1500.0,  1500.0, 57000.000),
    ('BNK-RA-006', 'BOQ-1.5',  'Site supervision (M14)',                                           'month',     15000.000,   12.0,    14.0,    14.0, 30000.000),
    ('BNK-RA-006', 'BOQ-6.3',  'RCC box culvert (cont.)',                                          'm',          850.000,    50.0,    65.0,    65.0, 12750.000),
    ('BNK-RA-006', 'BOQ-2.3',  'Existing road demolition (mid)',                                   'sqm',          1.000, 35000.0, 70000.0, 70000.0, 35000.000),

    ('BNK-RA-007', 'BOQ-4.3',  'DBM Phase 1 final',                                                'cum',         18.000, 17000.0, 21000.0, 21000.0, 72000.000),
    ('BNK-RA-007', 'BOQ-4.4',  'BC wearing course Phase 1 (cont.)',                                'cum',         32.000,  4500.0,  9500.0,  9500.0,160000.000),
    ('BNK-RA-007', 'BOQ-7.2',  'Sign boards + retro-reflective sheeting',                          'sqm',        320.000,    0.0,   240.0,   240.0, 76800.000),
    ('BNK-RA-007', 'BOQ-7.3',  'Thermoplastic road marking',                                       'sqm',         18.000,    0.0,  3500.0,  3500.0, 63000.000),
    ('BNK-RA-007', 'BOQ-1.5',  'Site supervision (M15)',                                           'month',     15000.000,   14.0,    15.0,    15.0, 15000.000),
    ('BNK-RA-007', 'BOQ-6.5',  'Stormwater outfall structures',                                    'each',      4500.000,    0.0,     8.0,     8.0, 36000.000),

    ('BNK-RA-008', 'BOQ-4.4',  'BC wearing course Phase 2',                                        'cum',         32.000,  9500.0, 14500.0, 14500.0,160000.000),
    ('BNK-RA-008', 'BOQ-7.4',  'Kerbstone precast — median + edge',                                'm',           14.500,    0.0,  6000.0,  6000.0, 87000.000),
    ('BNK-RA-008', 'BOQ-9.1',  'Median plantation — drip irrigation network',                      'm',           22.000,    0.0,  1800.0,  1800.0, 39600.000),
    ('BNK-RA-008', 'BOQ-1.5',  'Site supervision (M16)',                                           'month',     15000.000,   15.0,    16.0,    16.0, 15000.000),
    ('BNK-RA-008', 'BOQ-7.5',  'Cat-eye reflectors',                                               'each',         8.000,    0.0,  1500.0,  1500.0, 12000.000),
    ('BNK-RA-008', 'BOQ-2.4',  'Existing road demolition (north)',                                 'sqm',          1.000, 70000.0,100000.0,100000.0, 30000.000),

    ('BNK-RA-009', 'BOQ-4.4',  'BC wearing course final',                                          'cum',         32.000, 14500.0, 17000.0, 17000.0, 80000.000),
    ('BNK-RA-009', 'BOQ-8.1',  'OETC HT crossing relocation Phase 1 — UG cable',                   'm',          250.000,    0.0,   400.0,   400.0,100000.000),
    ('BNK-RA-009', 'BOQ-7.6',  'ITS conduit + chamber for VMS',                                    'm',           45.000,    0.0,  1500.0,  1500.0, 67500.000),
    ('BNK-RA-009', 'BOQ-1.5',  'Site supervision (M17)',                                           'month',     15000.000,   16.0,    17.0,    17.0, 15000.000),
    ('BNK-RA-009', 'BOQ-9.2',  'Tree planting — Acacia / Ghaf seedlings',                          'each',         28.000,    0.0,   800.0,   800.0, 22400.000),
    ('BNK-RA-009', 'BOQ-7.7',  'Final road marking — chevrons + stop lines',                       'sqm',         18.000,    0.0,   300.0,   300.0,  5400.000)
) AS i(bill_number, item_code, description, unit, rate, prev_qty, curr_qty, cum_qty, amount)
JOIN cost.ra_bills b ON b.bill_number = i.bill_number
                    AND b.project_id = (SELECT id FROM project.projects WHERE code = '6155')
WHERE NOT EXISTS (
    SELECT 1 FROM cost.ra_bill_items existing
    WHERE  existing.ra_bill_id = b.id
      AND  existing.item_code  = i.item_code
);
