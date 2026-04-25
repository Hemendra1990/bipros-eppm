-- 11-variation-orders.sql
-- Three VOs against the main EPC contract:
--   VO-001: APPROVED — soft marine clay treatment in Mahanadi flood plain
--   VO-002: APPROVED — Cyclone Dana embankment reinstatement (Force Majeure)
--   VO-003: RECOMMENDED — ROB addition at Cuttack approach over Howrah-Chennai trunk line
-- Check-constraint on status: INITIATED | RECOMMENDED | APPROVED | REJECTED.

INSERT INTO contract.variation_orders (
    id, created_at, updated_at, version,
    contract_id, vo_number, description, justification,
    vo_value, impact_on_budget, impact_on_schedule_days,
    status, approved_by, approved_at
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    c.id, v.vo_number, v.description, v.justification,
    v.vo_value, v.impact_on_budget, v.impact_on_schedule_days,
    v.status, v.approved_by, v.approved_at
FROM (VALUES
    ('VO-001',
     'Soft marine clay treatment Ch 22+000 to 24+000 (Mahanadi flood plain) — design revision.',
     'GI boreholes confirmed CBR<2% and N-value<4 till 4m depth at three locations. PMC peer-review (NIT Rourkela letter 2024-12-15) recommends lime+fly-ash stabilisation (5% by weight) + 200gsm geotextile. OWD CE approval Note OWD/SH10/VO-001/2025-014 dated 2025-04-10.',
       14000000.00,   14000000.00, 18, 'APPROVED',
     'Shri B.C. Sahoo, CE-OWD',  TIMESTAMP '2025-04-22 11:15:00+05:30'),
    ('VO-002',
     'Cyclone Dana embankment reinstatement Ch 13+800 to 14+600.',
     'Severe cyclonic storm Dana (24 Oct 2024 landfall) caused complete embankment washout in 800m segment. Force Majeure declared per Clause 19.2. CAR insurance paid ₹12M; VO covers balance reinstatement scope including ground improvement and re-laying GSB.',
       18000000.00,   18000000.00, 22, 'APPROVED',
     'Shri B.C. Sahoo, CE-OWD',  TIMESTAMP '2024-12-10 14:00:00+05:30'),
    ('VO-003',
     'ROB addition at Cuttack approach (4-span PSC girder over Howrah-Chennai trunk railway).',
     'OWD CE office direction dated 2025-12-08 to convert at-grade crossing at Ch 27+200 to a 4-span ROB over the Howrah-Chennai trunk line. RDSO scrutiny ongoing. RVNL Bhubaneswar coordination required for railway block windows.',
       65000000.00,   65000000.00, 45, 'RECOMMENDED',
      NULL, NULL)
) AS v(vo_number, description, justification, vo_value, impact_on_budget, impact_on_schedule_days, status, approved_by, approved_at)
CROSS JOIN (SELECT id FROM contract.contracts WHERE contract_number = 'SH10-EPC-2024-001') AS c;
