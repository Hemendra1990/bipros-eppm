-- BNK reports/14-tenders.sql — 2 tenders linked to Earthworks + Pavement subs.
--
-- Plan §"NHAI ref": 2 tenders, both AWARDED (CLOSED status), linked to
-- BNK-SUB-EARTH-2024 and BNK-SUB-PAVE-2025 subcontracts.
--
-- TenderStatus enum has DRAFT|PUBLISHED|BID_OPEN|EVALUATION|AWARDED|CANCELLED;
-- the plan's "CLOSED" maps to AWARDED.

-- ── Procurement plan (parent) ────────────────────────────────────────────
INSERT INTO contract.procurement_plans (
    id, created_at, updated_at, version,
    project_id, wbs_node_id,
    plan_code, description,
    procurement_method, status, approval_level,
    estimated_value, currency,
    target_nit_date, target_award_date,
    approved_by, approved_at
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, NULL,
       'BNK-PP-2024',
       'Procurement plan for Barka–Nakhal subcontract packages — Earthworks (south) and Pavement (north) — competitive open tender per MoTC procurement rules.',
       'OPEN_TENDER', 'COMPLETED', 'MOTC_PROCUREMENT_BOARD',
       20000000.000, 'OMR',
       DATE '2024-07-01', DATE '2024-12-31',
       'Eng. Khalid Al-Saadi, MoTC Project Director',
       TIMESTAMP '2024-06-20 14:00:00+04:00'
FROM project.projects p
WHERE p.code = '6155'
ON CONFLICT (plan_code) DO NOTHING;

-- ── Two tenders (both AWARDED) ───────────────────────────────────────────
INSERT INTO contract.tenders (
    id, created_at, updated_at, version,
    procurement_plan_id, project_id,
    tender_number, nit_date, bid_due_date, bid_open_date,
    completion_period_days, emd_amount, estimated_value,
    status, scope, awarded_contract_id
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       pp.id, pp.project_id,
       t.tender_number, t.nit_date, t.bid_due_date, t.bid_open_date,
       t.completion_days, t.emd, t.est_value,
       t.status, t.scope,
       (SELECT c.id FROM contract.contracts c WHERE c.contract_number = t.awarded_contract_number)
FROM (VALUES
    ('BNK-TND-2024-001',
     DATE '2024-07-15', DATE '2024-08-22', DATE '2024-08-25',
     365,  80000.000,  8000000.000, 'AWARDED',
     'Earthworks subcontract for Barka–Nakhal dualisation (Ch 0+000–20+500): mass excavation, embankment construction, subgrade preparation, and soft-strata treatment at Wadi crossings.  Performance-based 12-month maintenance.',
     'BNK-SUB-EARTH-2024'),
    ('BNK-TND-2025-002',
     DATE '2024-12-15', DATE '2025-02-08', DATE '2025-02-12',
     460, 120000.000, 12000000.000, 'AWARDED',
     'Pavement & Bituminous Works subcontract for Barka–Nakhal dualisation: GSB / WMM granular layers, DBM / BC bituminous layers, surface dressing, kerbstones, and pavement marking across full 41 km dualised carriageway.',
     'BNK-SUB-PAVE-2025')
) AS t(tender_number, nit_date, bid_due_date, bid_open_date,
       completion_days, emd, est_value, status, scope, awarded_contract_number)
JOIN contract.procurement_plans pp ON pp.plan_code = 'BNK-PP-2024'
ON CONFLICT (tender_number) DO NOTHING;
