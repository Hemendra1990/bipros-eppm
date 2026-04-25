-- 13-tenders.sql
-- Procurement plan + 1 awarded tender for the main EPC contract.
-- Fills the Compliance panel's CPPP (Central Public Procurement Portal) metric.
-- Check constraints:
--   procurement_plans.status:      DRAFT|APPROVED|IN_PROGRESS|COMPLETED|CANCELLED
--   procurement_plans.method:      OPEN_TENDER|LIMITED_TENDER|GEM_PORTAL|SINGLE_SOURCE|EOI|DESIGN_BUILD
--   tenders.status:                DRAFT|PUBLISHED|BID_OPEN|EVALUATION|AWARDED|CANCELLED

INSERT INTO contract.procurement_plans (
    id, created_at, updated_at, version,
    project_id, wbs_node_id,
    plan_code, description,
    procurement_method, status, approval_level,
    estimated_value, currency,
    target_nit_date, target_award_date,
    approved_by, approved_at
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.id, NULL,
    'SH10-PROC-001', 'Procurement plan for SH-10 Bhubaneswar-Cuttack 4-laning EPC contract (single-stage two-envelope bidding per OWD GFR 2017).',
    'OPEN_TENDER', 'COMPLETED', 'CHIEF_ENGINEER',
    4525000000.00, 'INR',
    DATE '2024-04-15', DATE '2024-07-15',
    'Shri B.C. Sahoo, CE-OWD', TIMESTAMP '2024-04-05 10:00:00+05:30'
FROM project.projects p WHERE p.code = 'OWD/SH10/OD/2025/001';

INSERT INTO contract.tenders (
    id, created_at, updated_at, version,
    procurement_plan_id, project_id,
    tender_number, nit_date, bid_due_date, bid_open_date,
    completion_period_days, emd_amount, estimated_value,
    status, scope
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    pp.id, pp.project_id,
    t.tender_number, t.nit_date, t.bid_due_date, t.bid_open_date,
    t.completion_days, t.emd, t.est_value,
    t.status, t.scope
FROM (VALUES
    ('SH10-EPC-TEND-2024',
     DATE '2024-04-20', DATE '2024-06-10', DATE '2024-06-15',
     870,   45250000.00, 4525000000.00, 'AWARDED',
     'Single-stage open EPC tender for SH-10 Bhubaneswar-Cuttack 4-laning per FIDIC Yellow Book conditions. Pre-qualification + technical + commercial envelopes. Pre-bid meeting held 2024-05-10. Awarded to Megha Engineering & Infrastructures Ltd on 2024-07-15 at the estimated value of ₹4,525,000,000.')
) AS t(tender_number, nit_date, bid_due_date, bid_open_date, completion_days, emd, est_value, status, scope)
CROSS JOIN (
    SELECT pp.id, pp.project_id FROM contract.procurement_plans pp
    WHERE pp.plan_code = 'SH10-PROC-001'
) AS pp;
