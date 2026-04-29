-- BNK reports/09-risks.sql — 12 Oman-specific risks for project 6155.
--
-- Plan §4.29: 8 OPEN/MITIGATING + 4 CLOSED, mixed RAG (RED/AMBER/GREEN).
-- Each risk has a deterministic identified date, due date, cost impact in
-- OMR, and schedule impact in days.  The legacy `affected_activities`
-- column is filled with comma-separated activity codes resolved by
-- OmanRoadProjectSupplementalSeeder later.

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
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       p.id, r.code, r.title, r.description,
       r.status, r.probability, r.impact,
       r.impact_cost, r.impact_schedule, r.risk_score, r.residual_risk_score,
       r.rag, r.trend, 'THREAT',
       r.identified_date, r.due_date,
       r.cost_impact_omr, r.schedule_impact_days,
       r.affected_activities, r.sort_order
FROM (VALUES
    ('R-001',
     'Land acquisition delay at Ch 18+200 (Barka outskirts)',
     'Three privately-held land parcels remain under acquisition dispute. Diwan of Royal Court compensation review pending. Delays embankment start on the most heavily-loaded segment of the corridor.',
     'OPEN_ESCALATED',                'HIGH',   'HIGH',   4, 5, 16.0, 12.0, 'RED',    'WORSENING', DATE '2024-11-08', DATE '2026-08-31',  450000.000, 45, 'ACT-3.1,ACT-3.2', 1),
    ('R-002',
     'MoTC environmental clearance for Wadi crossings',
     'Ministry of Transport, Communications & IT environmental clearance renewal for the three Wadi crossings (Wadi Mistal, Wadi Al Hattat, Wadi Far) is pending beyond the original lease boundary. Risks supply of GSB material if not resolved before khareef.',
     'MITIGATING',                    'HIGH',   'HIGH',   4, 4, 16.0, 10.0, 'RED',    'STABLE',    DATE '2025-01-15', DATE '2026-06-15',  250000.000, 30, 'ACT-5.1,ACT-5.2', 2),
    ('R-003',
     'Bitumen VG-30 supply volatility (Sohar refinery)',
     'OQ8 Sohar refinery turnaround maintenance scheduled during the BC layer execution window. Alternate sourcing from Bahrain BAPCO requires rate recast and customs documentation lead time.',
     'OPEN_BEING_MANAGED',            'MEDIUM', 'HIGH',   3, 4, 12.0,  6.0, 'AMBER',  'STABLE',    DATE '2025-04-22', DATE '2026-09-30',  150000.000, 15, 'ACT-4.3,ACT-4.4', 3),
    ('R-004',
     'OETC overhead 132 kV crossing relocation',
     'Oman Electricity Transmission Company directive to convert the overhead 132 kV HT line crossing at Ch 24+500 to underground RMU. Scope not in original BOQ — covered by VO-002 currently pending approval.',
     'OPEN_BEING_MANAGED',            'MEDIUM', 'MEDIUM', 3, 3,  9.0,  5.0, 'AMBER',  'IMPROVING', DATE '2025-06-30', DATE '2026-07-31',  320000.000, 20, 'ACT-8.1', 4),
    ('R-005',
     'Skilled labour shortage during Qaboos national day window',
     'Experienced paver operators and finishers exit-leave around Renaissance Day / National Day window (Nov–Dec). Replacement ramp-up via Indian/Bangladeshi mobilisation is 4–6 weeks.',
     'OPEN_MONITOR',                  'MEDIUM', 'MEDIUM', 3, 3,  9.0,  5.0, 'AMBER',  'STABLE',    DATE '2025-08-12', DATE '2026-12-31',   80000.000, 10, 'ACT-4.1,ACT-4.2,ACT-4.3', 5),
    ('R-006',
     'Forex exposure on imported retro-reflective sheeting',
     'High-intensity prismatic retro-reflective film is imported (3M USA / Avery UK).  USD↔OMR is pegged so direct exposure is small, but EUR↔OMR fluctuation of ±3% affects sign-board cost.',
     'OPEN_WATCH',                    'LOW',    'LOW',    2, 2,  4.0,  2.0, 'GREEN',  'STABLE',    DATE '2025-09-05', DATE '2026-08-15',   20000.000,  0, 'ACT-7.2', 6),
    ('R-007',
     'Khareef season impact on earthwork productivity',
     'Light khareef rains during Jul–Sep 2025 reduced compaction productivity by ~12 days on average.  Absorbed via 14-day schedule buffer plus crashed crew deployment.',
     'CLOSED',                        'MEDIUM', 'MEDIUM', 3, 3,  9.0,  0.0, 'GREEN',  'IMPROVING', DATE '2025-06-18', DATE '2025-10-31',  120000.000, 12, 'ACT-3.1,ACT-3.2,ACT-3.3', 7),
    ('R-008',
     'Soft strata at Wadi Al Hattat bridge Pier P3',
     'Unanticipated soft strata encountered at Pier P3 required deeper pile cap design (4 m additional depth, 8 additional bored cast-in-situ piles 1200 mm dia × 22 m).  Resolved via VO-001 approval.',
     'CLOSED',                        'HIGH',   'MEDIUM', 4, 3, 12.0,  0.0, 'GREEN',  'IMPROVING', DATE '2025-03-10', DATE '2025-11-30',  180000.000, 15, 'ACT-5.1', 8),
    ('R-009',
     'Heat stress / HSE incident risk during summer (May–Aug)',
     'Ambient temperatures of 45–50 °C with high humidity at Barka coastal stretch trigger Ministry of Manpower midday outdoor work ban.  Productivity loss + HSE incident risk during final BC layer push.',
     'OPEN_BEING_MANAGED',            'MEDIUM', 'MEDIUM', 3, 3,  9.0,  4.0, 'AMBER',  'STABLE',    DATE '2025-04-30', DATE '2026-08-31',   50000.000,  8, 'ACT-4.3,ACT-4.4', 9),
    ('R-010',
     'OPC supplier quality variance (Oman Cement)',
     'Variance in Oman Cement Co. OPC 53 grade compressive strength against IS 8112 / BS EN 197 testing.  Specifically, batch lot Q3-2025 showed 7-day strength 8% below spec.  Counter-sampling and dual-source from Raysut Cement.',
     'OPEN_MONITOR',                  'LOW',    'MEDIUM', 2, 3,  6.0,  3.0, 'AMBER',  'STABLE',    DATE '2025-09-22', DATE '2026-10-31',   70000.000,  5, 'ACT-5.1,ACT-5.2', 10),
    ('R-011',
     'Crusher permit renewal for Wadi Mistal quarry',
     'Ministry of Energy & Minerals quarry lease renewal at Wadi Mistal for crushed aggregate.  Original lease expires Jul 2026 with 90-day renewal lead time.  Alternate sourcing 38 km further adds haulage cost.',
     'OPEN_BEING_MANAGED',            'MEDIUM', 'MEDIUM', 3, 3,  9.0,  4.0, 'AMBER',  'IMPROVING', DATE '2025-11-12', DATE '2026-07-31',   90000.000, 10, 'ACT-3.2,ACT-3.3,ACT-4.1', 11),
    ('R-012',
     'RFCT compensation dispute on Nakhal approach',
     'Royal Family Crown Trust land at Nakhal approach (Ch 38+800–40+200) had compensation dispute under MoTC Land Acquisition Act.  Resolved via expedited Diwan referral and revised valuation.',
     'CLOSED',                        'HIGH',   'HIGH',   4, 4, 16.0,  0.0, 'GREEN',  'IMPROVING', DATE '2024-12-05', DATE '2025-09-30',  200000.000, 20, 'ACT-3.3', 12)
) AS r(code, title, description,
       status, probability, impact,
       impact_cost, impact_schedule, risk_score, residual_risk_score,
       rag, trend, identified_date, due_date,
       cost_impact_omr, schedule_impact_days, affected_activities, sort_order)
CROSS JOIN (SELECT id FROM project.projects WHERE code = '6155') AS p
WHERE NOT EXISTS (
    SELECT 1 FROM risk.risks rr
    WHERE  rr.project_id = p.id
      AND  rr.code       = r.code
);
