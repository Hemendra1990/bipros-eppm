-- 11-variation-orders.sql
-- Two VOs against the main contract:
--   VO-001: APPROVED — soft strata at bridge foundation (drove R-008 closure)
--   VO-002: RECOMMENDED (IN_REVIEW) — utility shifting at Ch 157+800
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
     'Additional scope for bridge pier P2 foundation due to unanticipated soft strata encountered at 6 m depth. Design revision: 4 m deeper pile cap, 12 additional bored cast-in-situ piles (1200 mm dia × 18 m), and peer-review by IIT-Roorkee geotechnical cell.',
     'Discovery during pier P2 excavation on 2025-04-02. Soil investigation by IRC-SP:54 methodology confirmed N-value < 6 till 9 m RL. Peer review (IIT-Roorkee letter dated 2025-06-18) recommends deeper founding. NHAI RO Jaipur approval per note IB-NHAI/RJ/NH48/VO-001 dated 2025-08-12.',
      125000000.00,  125000000.00, 15, 'APPROVED',
     'Shri A.P. Sharma, CGM-NHAI (RJ)',  TIMESTAMP '2025-09-15 10:30:00+05:30'),
    ('VO-002',
     'Utility shifting at Ch 157+800: convert overhead 132 kV HT transmission line to underground per PWD Rajasthan directive. Scope: 1.2 km underground cable laying + two RMU (Ring Main Unit) substations + road crossings reinstatement.',
     'PWD Rajasthan communication dated 2026-02-15 (Ref: PWD/Elec/NH48/2026-007) mandating OHL-to-UG conversion to clear aerial obstruction for the widened 4-lane corridor. Original BOQ did not include this scope. JICA loan covenant compliance also requires it.',
       32000000.00,   32000000.00,  8, 'RECOMMENDED',
      NULL, NULL)
) AS v(vo_number, description, justification, vo_value, impact_on_budget, impact_on_schedule_days, status, approved_by, approved_at)
CROSS JOIN (SELECT id FROM contract.contracts WHERE contract_number = 'NH48-MAIN-2024-001') AS c;
