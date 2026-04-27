-- 08-risks.sql
-- Realistic risk register for a 4-lane NHAI widening project:
-- 2 RED active (land acquisition, environmental clearance),
-- 3 AMBER being managed (bitumen supply, utility shifting, labour),
-- 1 GREEN watch (forex on imports), 2 CLOSED (monsoon + bridge soft strata).

INSERT INTO risk.risks (
    id, created_at, updated_at, version,
    project_id, code, title, description,
    status, probability, impact,
    impact_cost, impact_schedule, risk_score, residual_risk_score,
    rag, trend, risk_type,
    identified_date, due_date,
    cost_impact, schedule_impact_days,
    affected_activities, sort_order
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    p.id, r.code, r.title, r.description,
    r.status, r.probability, r.impact,
    r.impact_cost, r.impact_schedule, r.risk_score, r.residual_risk_score,
    r.rag, r.trend, 'THREAT',
    r.identified_date, r.due_date,
    r.cost_impact_rupees, r.schedule_impact_days,
    r.affected_activities, r.sort_order
FROM (VALUES
    ('R-001',
     'Land acquisition delay at Ch 152+500 to Ch 154+200',
     'Three private land parcels remain under acquisition dispute with the Land Acquisition Officer. Award notifications issued but compensation disagreement is pending RFCTLARR arbitration. Delays embankment start on the most heavily-loaded segment.',
     'LAND_ACQUISITION', 'OPEN_ESCALATED',        'HIGH',     'HIGH',     4, 5, 16.0, 12.0, 'RED',    'WORSENING', DATE '2025-02-12', DATE '2026-06-30',   45000000.00, 45, 'ACT-1.1,ACT-1.2', 1),
    ('R-002',
     'Environmental clearance renewal for Ch 155+000 quarry access',
     'State Environment Impact Assessment Authority renewal pending beyond the quarry lease boundary extension. Impacts GSB supply if not resolved before monsoon 2026.',
     'STATUTORY_CLEARANCE', 'MITIGATING',          'HIGH',     'HIGH',     4, 4, 16.0, 10.0, 'RED',    'STABLE',    DATE '2025-11-08', DATE '2026-05-15',   25000000.00, 30, 'ACT-2.1,ACT-2.2', 2),
    ('R-003',
     'Bitumen VG-30 supply volatility from IOCL Panipat',
     'Refinery turnaround maintenance scheduled during the BC layer execution window. Alternate sourcing from HPCL Mathura requires rate recast.',
     'MARKET_PRICE', 'OPEN_UNDER_ACTIVE_MANAGEMENT','MEDIUM',   'HIGH',     3, 4, 12.0,  6.0, 'AMBER',  'STABLE',    DATE '2025-06-20', DATE '2026-09-30',   15000000.00, 15, 'ACT-3.1,ACT-3.2', 3),
    ('R-004',
     'Underground utility shifting at Ch 157+800',
     'PWD 132 kV HT overhead line crossing needs to go underground per state directive. Scope not in original BOQ — VO-002 under review.',
     'UTILITY_SHIFTING', 'OPEN_BEING_MANAGED',    'MEDIUM',   'MEDIUM',   3, 3,  9.0,  4.0, 'AMBER',  'IMPROVING', DATE '2025-09-10', DATE '2026-07-31',   32000000.00, 20, 'ACT-5.1,ACT-5.2', 4),
    ('R-005',
     'Skilled labour shortage during monsoon withdrawal phase',
     'Experienced paver operators migrating to competing NHAI packages in Haryana and UP.  Local contractor bench thin; replacement ramp-up lead time 4–6 weeks.',
     'RESOURCE', 'OPEN_MONITOR',                  'MEDIUM',   'MEDIUM',   3, 3,  9.0,  5.0, 'AMBER',  'STABLE',    DATE '2025-08-22', DATE '2026-06-30',    8000000.00, 10, 'ACT-3.1,ACT-3.2', 5),
    ('R-006',
     'Forex fluctuation on imported retro-reflective sheeting',
     'High-intensity prismatic film is imported from 3M USA.  Rupee volatility of ±3% directly affects sign-board cost.  Low exposure; monitoring only.',
     'MARKET_PRICE', 'OPEN_WATCH',                'LOW',      'LOW',      2, 2,  4.0,  2.0, 'GREEN',  'STABLE',    DATE '2025-10-05', DATE '2026-08-15',    2000000.00,  0, 'ACT-5.2', 6),
    ('R-007',
     'Monsoon 2025 impact on earthwork productivity',
     'Unseasonal rainfall extended monsoon by ~22 days.  Absorbed via 14-day schedule buffer plus crashed crew deployment in Aug–Sep 2025.',
     'MONSOON_IMPACT', 'CLOSED',                  'MEDIUM',   'MEDIUM',   3, 3,  9.0,  0.0, 'GREEN',  'IMPROVING', DATE '2025-07-18', DATE '2025-09-30',   12000000.00, 12, 'ACT-1.1,ACT-1.2,ACT-4.1', 7),
    ('R-008',
     'Soft strata discovered at bridge Pier P2 foundation',
     'Unanticipated soft strata required 4 m deeper pile cap design at Pier P2.  Resolved via VO-001 approval and design revision by IIT-Roorkee peer review.',
     'TECHNICAL', 'CLOSED',                        'HIGH',     'MEDIUM',   4, 3, 12.0,  0.0, 'GREEN',  'IMPROVING', DATE '2025-04-02', DATE '2025-10-31',   18000000.00, 15, 'ACT-6.1', 8)
) AS r(code, title, description, category, status, probability, impact, impact_cost, impact_schedule, risk_score, residual_risk_score, rag, trend, identified_date, due_date, cost_impact_rupees, schedule_impact_days, affected_activities, sort_order)
CROSS JOIN (SELECT id FROM project.projects WHERE code = 'BIPROS/NHAI/RJ/2025/001') AS p;
