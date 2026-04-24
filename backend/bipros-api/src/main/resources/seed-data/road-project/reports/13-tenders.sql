-- 13-tenders.sql
-- Procurement plan + 3 tenders that fill the Compliance panel's CPPP
-- (Central Public Procurement Portal) metric.
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
    'NH48-PP-2025', 'Ancillary scope procurement plan for NH-48 Rajasthan widening — signage, plantation, electrical & ITS, safety audit consultancy.',
    'OPEN_TENDER', 'APPROVED', 'CGM_NHAI',
    230000000.00, 'INR',
    DATE '2025-09-01', DATE '2026-03-31',
    'Shri A.P. Sharma, CGM-NHAI (RJ)', TIMESTAMP '2025-08-15 14:00:00+05:30'
FROM project.projects p WHERE p.code = 'BIPROS/NHAI/RJ/2025/001';

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
    ('NH48-TND-2025-001',
     DATE '2025-10-15', DATE '2025-11-20', DATE '2025-11-22',
     180,    900000.00, 90000000.00, 'AWARDED',
     'Supply, installation and testing of retro-reflective sign boards, overhead gantries and chevron markers along the NH-48 widening stretch per IRC:67 and IRC:35. Includes 3-year performance warranty.'),
    ('NH48-TND-2025-002',
     DATE '2025-12-01', DATE '2026-01-10', DATE '2026-01-12',
     365,    600000.00, 60000000.00, 'EVALUATION',
     'Avenue plantation and ROW landscaping along 20 km widening corridor including drip irrigation infrastructure, 3-year maintenance and CAMPA compliance.'),
    ('NH48-TND-2026-003',
     DATE '2026-02-10', DATE '2026-03-25', DATE '2026-03-27',
     300,    800000.00, 80000000.00, 'PUBLISHED',
     'Intelligent Transportation System works — ATCC cameras, VMS boards, emergency call boxes, fibre backbone, and central control room integration for the NH-48 Rajasthan stretch.')
) AS t(tender_number, nit_date, bid_due_date, bid_open_date, completion_days, emd, est_value, status, scope)
CROSS JOIN (
    SELECT pp.id, pp.project_id FROM contract.procurement_plans pp
    WHERE pp.plan_code = 'NH48-PP-2025'
) AS pp;
