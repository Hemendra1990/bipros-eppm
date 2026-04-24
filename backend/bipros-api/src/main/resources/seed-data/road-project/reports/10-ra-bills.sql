-- 10-ra-bills.sql
-- Four Running Account bills against the main contract (NH48-MAIN-2024-001).
-- Mix of states (2 PAID, 1 APPROVED awaiting payment, 1 SUBMITTED under
-- PMC review).  Deductions follow standard NHAI structure:
-- 5% retention + 2% TDS (IT) + 18% GST reverse charge + mob advance
-- recovery.  All amounts in rupees.

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
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    c.project_id, c.id, 'NH48-MAIN', b.bill_number,
    b.period_from, b.period_to,
    b.gross, b.retention, b.tds, b.gst, b.mob_recovery,
    b.net, b.cumulative,
    b.ai_pct, b.claimed_pct, b.gate, b.variance,
    b.status,
    b.submitted, b.certified, b.approved, b.paid, b.paid,
    b.certified_by, b.approved_by,
    b.pfms_ref, b.remarks
FROM (VALUES
    ('NH48-RA-001',
     DATE '2025-01-15', DATE '2025-04-30',
      420000000.00,  21000000.00,   8400000.00,  75600000.00,   42000000.00,
      273000000.00,  420000000.00,
      22.5, 24.0, 'PASS', 1.50, 'PAID',
      DATE '2025-05-10', DATE '2025-05-28', DATE '2025-06-10', DATE '2025-06-25',
      'Shri R.K. Meena, PMC DGM', 'Shri A.P. Sharma, CGM-NHAI',
      'PFMS-DPA/NH48/2025-001/042', 'First RA — mobilisation + initial earthwork milestones met.'),
    ('NH48-RA-002',
     DATE '2025-05-01', DATE '2025-09-30',
      780000000.00,  39000000.00,  15600000.00, 140400000.00,   48000000.00,
      537000000.00, 1200000000.00,
      48.0, 50.5, 'PASS', 2.50, 'PAID',
      DATE '2025-10-12', DATE '2025-11-08', DATE '2025-11-25', DATE '2025-12-18',
      'Shri R.K. Meena, PMC DGM', 'Shri A.P. Sharma, CGM-NHAI',
      'PFMS-DPA/NH48/2025-002/187', 'Embankment + GSB/WMM handover + bridge substructure completion.'),
    ('NH48-RA-003',
     DATE '2025-10-01', DATE '2026-02-28',
      660000000.00,  33000000.00,  13200000.00, 118800000.00,   30000000.00,
      465000000.00, 1860000000.00,
      72.0, 74.5, 'PASS', 2.50, 'APPROVED',
      DATE '2026-03-12', DATE '2026-03-28', DATE '2026-04-10', NULL,
      'Shri R.K. Meena, PMC DGM', 'Shri A.P. Sharma, CGM-NHAI',
       NULL, 'Bridge superstructure + DBM partial quantities.  Payment pending PFMS DPA release.'),
    ('NH48-RA-004',
     DATE '2026-03-01', DATE '2026-04-15',
      290000000.00,  14500000.00,   5800000.00,  52200000.00,   12000000.00,
      205500000.00, 2150000000.00,
      87.0, 89.5, 'HOLD_VARIANCE', 2.50, 'SUBMITTED',
      DATE '2026-04-18', NULL, NULL, NULL,
      NULL, NULL,
      NULL, 'DBM completion + BC layer partial + drainage tie-in.  Under PMC certification review.')
) AS b(bill_number, period_from, period_to, gross, retention, tds, gst, mob_recovery, net, cumulative,
       ai_pct, claimed_pct, gate, variance, status, submitted, certified, approved, paid,
       certified_by, approved_by, pfms_ref, remarks)
CROSS JOIN (
    SELECT c.id, c.project_id FROM contract.contracts c WHERE c.contract_number = 'NH48-MAIN-2024-001'
) AS c;
