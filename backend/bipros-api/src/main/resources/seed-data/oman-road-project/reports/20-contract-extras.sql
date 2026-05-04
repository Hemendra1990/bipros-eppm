-- BNK reports/20-contract-extras.sql — Five contract sub-tables for the BNK project:
--   • contract.contract_milestones      (12 rows: 3 milestones × 4 contracts)
--   • contract.performance_bonds        (4 rows : 1 bond per contract)
--   • contract.procurement_plans        (3 rows  for tendering pipeline)
--   • contract.bid_submissions          (6 rows : 3 bidders per tender)
--   • contract.contractor_scorecards    (4 rows : 1 quarterly scorecard per contract)
-- Currency throughout = OMR (3-decimal, but column is numeric(15,2)).

-- ─────────────────────────── Contract Milestones ───────────────────────────
-- 3 milestones per contract: Mobilization (10%), Mid-point (40%), Substantial Completion (45%) — last 5% paid on DLP.
INSERT INTO contract.contract_milestones (
    id, created_at, updated_at, version,
    contract_id, milestone_code, milestone_name,
    target_date, actual_date, amount, payment_percentage, status
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       c.id, m.code, m.name,
       m.target_date, m.actual_date, m.amount, m.pct, m.status
FROM (VALUES
    -- Main contract (50M OMR)
    ('BNK-MAIN-2024-001','MS-MAIN-MOB',  'Mobilization & Site Establishment',
        DATE '2024-10-15', DATE '2024-10-22',  5000000.00, 10.0, 'ACHIEVED'),
    ('BNK-MAIN-2024-001','MS-MAIN-50',   '50% Physical Completion',
        DATE '2025-09-30', NULL,              20000000.00, 40.0, 'DELAYED'),
    ('BNK-MAIN-2024-001','MS-MAIN-SC',   'Substantial Completion',
        DATE '2026-08-31', NULL,              22500000.00, 45.0, 'PENDING'),

    -- Earthworks subcontract (8M OMR)
    ('BNK-SUB-EARTH-2024','MS-EARTH-MOB','Plant & Crew Mobilization',
        DATE '2024-10-15', DATE '2024-10-12',   800000.00, 10.0, 'ACHIEVED'),
    ('BNK-SUB-EARTH-2024','MS-EARTH-50', 'Embankment 50% Compaction',
        DATE '2025-04-15', DATE '2025-04-20',  3200000.00, 40.0, 'ACHIEVED'),
    ('BNK-SUB-EARTH-2024','MS-EARTH-COMP','Earthworks Substantial Completion',
        DATE '2025-09-30', NULL,               3600000.00, 45.0, 'PENDING'),

    -- Pavement subcontract (12M OMR)
    ('BNK-SUB-PAVE-2025','MS-PAVE-MOB',  'Asphalt Plant Commissioning',
        DATE '2025-06-15', DATE '2025-06-20',  1200000.00, 10.0, 'ACHIEVED'),
    ('BNK-SUB-PAVE-2025','MS-PAVE-DBM',  'DBM Layer 50% Carriageway',
        DATE '2026-02-28', NULL,               4800000.00, 40.0, 'DELAYED'),
    ('BNK-SUB-PAVE-2025','MS-PAVE-SC',   'BC Layer & Surface Dressing Complete',
        DATE '2026-08-15', NULL,               5400000.00, 45.0, 'PENDING'),

    -- Bridges subcontract (15M OMR)
    ('BNK-SUB-BRIDGE-2025','MS-BR-MOB',  'Pile-Boring Rig Mobilization',
        DATE '2025-03-01', DATE '2025-03-08',  1500000.00, 10.0, 'ACHIEVED'),
    ('BNK-SUB-BRIDGE-2025','MS-BR-SUB',  'All Substructure Complete (Piles + Pier)',
        DATE '2025-12-15', NULL,               6000000.00, 40.0, 'DELAYED'),
    ('BNK-SUB-BRIDGE-2025','MS-BR-SUP',  'Superstructure & Wearing Coat Complete',
        DATE '2026-06-30', NULL,               6750000.00, 45.0, 'PENDING')
) AS m(contract_number, code, name, target_date, actual_date, amount, pct, status)
JOIN contract.contracts c ON c.contract_number = m.contract_number
WHERE NOT EXISTS (
    SELECT 1 FROM contract.contract_milestones x
    WHERE x.contract_id = c.id AND x.milestone_code = m.code
);

-- ─────────────────────────── Performance Bonds ───────────────────────────
-- 10% of contract value, issued by Omani banks, valid till DLP end. RETENTION bond on main only.
INSERT INTO contract.performance_bonds (
    id, created_at, updated_at, version,
    contract_id, bond_type, bond_value, bank_name,
    issue_date, expiry_date, status
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       c.id, b.bond_type, b.bond_value, b.bank_name,
       b.issue_date, b.expiry_date, b.status
FROM (VALUES
    ('BNK-MAIN-2024-001',   'PERFORMANCE_GUARANTEE', 5000000.00, 'Bank Muscat SAOG',
        DATE '2024-08-25', DATE '2028-08-31', 'ACTIVE'),
    ('BNK-SUB-EARTH-2024',  'PERFORMANCE_GUARANTEE',  800000.00, 'National Bank of Oman',
        DATE '2024-09-20', DATE '2026-09-30', 'ACTIVE'),
    ('BNK-SUB-PAVE-2025',   'PERFORMANCE_GUARANTEE', 1200000.00, 'HSBC Bank Oman',
        DATE '2025-04-30', DATE '2028-02-28', 'ACTIVE'),
    ('BNK-SUB-BRIDGE-2025', 'ADVANCE_GUARANTEE',     1500000.00, 'Sohar International Bank',
        DATE '2025-01-25', DATE '2026-12-31', 'ACTIVE')
) AS b(contract_number, bond_type, bond_value, bank_name, issue_date, expiry_date, status)
JOIN contract.contracts c ON c.contract_number = b.contract_number
WHERE NOT EXISTS (
    SELECT 1 FROM contract.performance_bonds x
    WHERE x.contract_id = c.id AND x.bond_type = b.bond_type
);

-- ─────────────────────────── Procurement Plans ───────────────────────────
-- Three upstream packages still in tendering pipeline: street lighting, ITS, landscaping.
INSERT INTO contract.procurement_plans (
    id, created_at, updated_at, version,
    project_id, plan_code, description,
    procurement_method, estimated_value, currency,
    target_nit_date, target_award_date,
    status, approval_level, approved_by, approved_at,
    wbs_node_id
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, pp.plan_code, pp.description,
       pp.procurement_method, pp.estimated_value, 'OMR',
       pp.target_nit_date, pp.target_award_date,
       pp.status, pp.approval_level, pp.approved_by, pp.approved_at,
       (SELECT id FROM project.wbs_nodes WHERE project_id = p.id AND code = pp.wbs_code)
FROM (VALUES
    ('BNK-PP-LIGHT-2026',
     'Street Lighting & Electrical Package — solar-LED dual-arm luminaires across 41 km, junction box upgrades, OETC interface civil works, and 18-month O&M.',
     'OPEN_TENDER',  2400000.00, DATE '2026-06-01', DATE '2026-09-15',
     'APPROVED',  'TENDER_BOARD', 'MoTC Project Director', TIMESTAMP '2026-04-15 10:00:00+04:00',
     'WBS-7'),

    ('BNK-PP-ITS-2026',
     'Intelligent Transport Systems package — VMS gantries (5 nos.), CCTV (24 cameras), incident detection, and SCADA integration with MoTC central traffic control room.',
     'LIMITED_TENDER', 1800000.00, DATE '2026-05-15', DATE '2026-08-30',
     'IN_PROGRESS','PROCUREMENT_BOARD', 'MoTC Procurement Director', TIMESTAMP '2026-04-08 14:30:00+04:00',
     'WBS-7'),

    ('BNK-PP-LAND-2026',
     'Landscaping & median greening — 12,000 native saplings (Acacia / Ghaf / Sidr), drip irrigation network, decorative stonework at junctions.',
     'OPEN_TENDER',   650000.00, DATE '2026-07-15', DATE '2026-10-30',
     'DRAFT', NULL, NULL, NULL,
     'WBS-9')
) AS pp(plan_code, description, procurement_method, estimated_value,
        target_nit_date, target_award_date,
        status, approval_level, approved_by, approved_at,
        wbs_code)
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
WHERE NOT EXISTS (
    SELECT 1 FROM contract.procurement_plans existing
    WHERE  existing.plan_code = pp.plan_code
);

-- ─────────────────────────── Bid Submissions ───────────────────────────
-- 3 bidders per tender; first tender already AWARDED, second still in evaluation.
INSERT INTO contract.bid_submissions (
    id, created_at, updated_at, version,
    tender_id, bidder_code, bidder_name,
    technical_score, financial_bid,
    status, evaluation_remarks
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       t.id, bs.bidder_code, bs.bidder_name,
       bs.tech, bs.fin, bs.status, bs.remarks
FROM (VALUES
    ('BNK-TND-2024-001', 'GALFAR-CR-1054678', 'Galfar Engineering & Contracting SAOG',
       89.5, 50000000.00, 'AWARDED',
       'L1 by 2.4% combined score (technical 70% + financial 30%); strong wadi-crossing track record.'),
    ('BNK-TND-2024-001', 'CCC-CR-9087712',    'Consolidated Contractors Company (Oman)',
       86.2, 51200000.00, 'TECHNICALLY_QUALIFIED',
       'Compliant. Marginally higher financial; alt-pavement proposal not accepted by Engineer.'),
    ('BNK-TND-2024-001', 'STRABAG-CR-3214567','Strabag Oman LLC',
       82.0, 53400000.00, 'TECHNICALLY_QUALIFIED',
       'Compliant; later awarded the pavement subcontract via separate tender BNK-TND-2025-002.'),

    ('BNK-TND-2025-002', 'STRABAG-CR-3214567','Strabag Oman LLC',
       91.0, 12000000.00, 'AWARDED',
       'L1; superior asphalt-mix design, plant capacity > 250 TPH within 35 km of corridor.'),
    ('BNK-TND-2025-002', 'COLAS-CR-5511002',  'Colas Rail Oman LLC',
       87.5, 12450000.00, 'TECHNICALLY_QUALIFIED',
       'Compliant; second-lowest financial bid by 3.75%.'),
    ('BNK-TND-2025-002', 'TARMAC-CR-6612893', 'Tarmac Oman LLC',
       73.5, 13100000.00, 'NOT_QUALIFIED',
       'Disqualified at technical stage — failed minimum experience criterion (no >100 TPH plant in fleet).')
) AS bs(tender_number, bidder_code, bidder_name, tech, fin, status, remarks)
JOIN contract.tenders t ON t.tender_number = bs.tender_number
WHERE NOT EXISTS (
    SELECT 1 FROM contract.bid_submissions x
    WHERE x.tender_id = t.id AND x.bidder_code = bs.bidder_code
);

-- ─────────────────────────── Contractor Scorecards ───────────────────────────
-- One quarterly scorecard per contract, period = '2026-Q1'. Aligned with contract SPI/CPI.
INSERT INTO contract.contractor_scorecards (
    id, created_at, updated_at, version,
    contract_id, period,
    overall_score, progress_score, quality_score, safety_score, payment_compliance_score,
    remarks
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       c.id, '2026-Q1',
       sc.overall, sc.progress, sc.quality, sc.safety, sc.payment,
       sc.remarks
FROM (VALUES
    ('BNK-MAIN-2024-001',   78.5, 75.0, 82.0, 84.0, 73.0,
     'Q1-2026 review: schedule slippage on superstructure work; quality and safety remain strong.'),
    ('BNK-SUB-EARTH-2024',  84.0, 92.0, 80.0, 78.0, 86.0,
     'Q1-2026 review: nearing completion ahead of schedule; minor compaction NCRs closed in time.'),
    ('BNK-SUB-PAVE-2025',   76.0, 68.0, 79.0, 82.0, 75.0,
     'Q1-2026 review: DBM layer behind plan, asphalt plant downtime in Feb impacted production.'),
    ('BNK-SUB-BRIDGE-2025', 72.0, 60.0, 78.0, 80.0, 70.0,
     'Q1-2026 review: pier P3 redesign and unforeseen ground conditions caused delay; mitigation underway.')
) AS sc(contract_number, overall, progress, quality, safety, payment, remarks)
JOIN contract.contracts c ON c.contract_number = sc.contract_number
WHERE NOT EXISTS (
    SELECT 1 FROM contract.contractor_scorecards x
    WHERE x.contract_id = c.id AND x.period = '2026-Q1'
);
