-- 08-risks.sql
-- 12 active risks for the Odisha SH-10 demo project, drawn from the
-- Risk Master Metadata DOCX framework and adapted for OWD/Odisha context.
-- Each risk also seeds 2-4 RiskResponse rows from the DOCX
-- Avoid/Mitigate/Transfer/Accept/Contingency strategies.
--
-- Mix: 2 CRITICAL/RED, 8 HIGH or AMBER under management, 2 CLOSED.

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
    ('OD-LA-01',
     'Cuttack Ring-Road acquisition stuck — 3 parcels under court (RK-LA-01)',
     'Three private land parcels at the Cuttack approach (Ch 26+200 to Ch 27+800) remain in dispute with the Land Acquisition Officer. Compensation under 2013 RFCTLARR Act is contested by landowners; matter pending in Cuttack district court since Mar 2025. Blocks ROB approach earthwork on the most heavily-loaded segment.',
     'LAND_ACQUISITION', 'OPEN_ESCALATED',          'HIGH',     'HIGH',     4, 5, 20.0, 12.0, 'RED',    'WORSENING', DATE '2025-03-15', DATE '2026-09-30',  32000000.00, 75, 'ACT-1.1,ACT-6.3', 1),
    ('OD-FIN-01',
     'OWD payment cycle stretched to 60 days vs 28-day contract (RK-FIN-01)',
     'OWD/PIU Bhubaneswar processing IPCs in 50-60 days vs the 28-day contractual window. Three months of working-capital stress at MEIL; subcontractor payment claims accumulating. Risk of cascading subcontractor walk-off.',
     'CONTRACTOR_FINANCIAL', 'OPEN_BEING_MANAGED',  'MEDIUM',   'HIGH',     3, 4, 12.0,  6.0, 'AMBER',  'STABLE',    DATE '2025-04-22', DATE '2026-12-31',  80000000.00, 30, 'ACT-3.1,ACT-3.2', 2),
    ('OD-MW-01',
     'Cyclone Dana (Oct 2024) damaged ongoing embankment Ch 14+200 (RK-MW-01)',
     'Severe cyclonic storm Dana made landfall on 24 Oct 2024 with 120 km/h winds and 280 mm rainfall in 36 hours. Active embankment section between Ch 13+800 and Ch 14+600 was washed out. Repair completed under VO-002.',
     'NATURAL_HAZARD', 'CLOSED',                    'HIGH',     'HIGH',     4, 4, 16.0,  0.0, 'GREEN',  'IMPROVING', DATE '2024-10-22', DATE '2024-12-15',  18000000.00, 22, 'ACT-1.2', 3),
    ('OD-MAT-01',
     'Bitumen VG-30 supply from Paradip refinery — turnaround Q3 2026 (RK-MAT-01)',
     'IndianOil Paradip refinery scheduled turnaround maintenance Aug-Sep 2026 will halt VG-30 supply during the BC paving window. Alternate sourcing from HPCL Mathura adds ₹4,200/MT and 5-day transit lead time.',
     'MARKET_PRICE', 'OPEN_UNDER_ACTIVE_MANAGEMENT','MEDIUM',   'HIGH',     3, 4, 12.0,  6.0, 'AMBER',  'STABLE',    DATE '2026-01-20', DATE '2026-08-31',  22000000.00, 12, 'ACT-3.1,ACT-3.2,ACT-3.4', 4),
    ('OD-ENV-01',
     'Wildlife NOC delay — alignment skirts Chandaka WLS buffer (RK-ENV-01)',
     'SH-10 alignment between Ch 6+200 and Ch 8+800 passes through the 1-km eco-sensitive buffer of Chandaka-Dampada Wildlife Sanctuary. Stage-II forest clearance from MoEFCC is pending since Sep 2024; State Wildlife Board NOC also required.',
     'FOREST_CLEARANCE', 'MITIGATING',              'MEDIUM',   'HIGH',     3, 5, 15.0,  9.0, 'RED',    'STABLE',    DATE '2024-09-10', DATE '2026-06-30',  28000000.00, 90, 'ACT-1.1,ACT-1.2,ACT-2.1', 5),
    ('OD-UTIL-01',
     'OPTCL 220 kV line shifting at Ch 8+500 — pending OPTCL approval (RK-LA-03)',
     'Odisha Power Transmission Corporation Ltd 220 kV transmission line crosses SH-10 at Ch 8+500. Joint inspection signed Mar 2025; OPTCL shifting design approved Jul 2025; execution pending OPTCL outage permission.',
     'UTILITY_SHIFTING', 'OPEN_BEING_MANAGED',      'HIGH',     'MEDIUM',   4, 3, 12.0,  6.0, 'AMBER',  'IMPROVING', DATE '2025-02-08', DATE '2026-09-30',  19000000.00, 25, 'ACT-7.1', 6),
    ('OD-GEO-01',
     'Soft marine clay zone Ch 22+000–24+000 (Mahanadi flood plain) (RK-GEO-02)',
     'GI boreholes at Ch 22+400, 23+100 and 23+800 confirmed soft marine clay (CBR < 2%, N-value < 4) up to 4m depth. Embankment requires lime-fly-ash stabilisation + geotextile separation. Design revised Dec 2024 under VO-001.',
     'TECHNICAL', 'MITIGATING',                     'MEDIUM',   'MEDIUM',   3, 3,  9.0,  4.0, 'AMBER',  'STABLE',    DATE '2024-11-18', DATE '2026-08-31',  14000000.00, 30, 'ACT-1.2,ACT-1.3', 7),
    ('OD-LO-01',
     'Local protest at Khurda boundary toll plaza site (RK-LO-01)',
     'Khurda district panchayat opposing the proposed toll plaza site at Ch 12+400 over land compensation rates. Two written representations to Collector; minor dharna on 15 Aug 2025. No work stoppage yet but escalation risk during toll plaza construction.',
     'EXTERNAL', 'OPEN_MONITOR',                    'MEDIUM',   'MEDIUM',   3, 3,  9.0,  6.0, 'AMBER',  'STABLE',    DATE '2025-08-15', DATE '2026-10-31',   8000000.00, 20, 'ACT-7.2', 8),
    ('OD-LAB-01',
     'Skilled paver-operator shortage post-Diwali 2025 (RK-LAB-01)',
     'Two senior paver operators migrated to a competing NHAI package in Andhra Pradesh in Nov 2025. Replacement crew onboarded by Jan 2026 from Tamil Nadu after a 6-week ramp-up. Productivity loss absorbed via crashed schedule.',
     'RESOURCE', 'CLOSED',                          'MEDIUM',   'MEDIUM',   3, 3,  9.0,  0.0, 'GREEN',  'IMPROVING', DATE '2025-10-22', DATE '2026-01-15',   4500000.00, 14, 'ACT-3.1,ACT-3.2', 9),
    ('OD-HSE-01',
     'Coastal humidity affecting bituminous mat compaction quality (RK-HSE-03)',
     'Relative humidity > 85% during Jun-Sep monsoon transition is reducing BC layer surface compaction. Three core test results in Jul-Aug 2025 showed density 96.2-96.8% vs 98% spec. Mix design revised, anti-stripping additive ratio increased.',
     'HEALTH_SAFETY', 'OPEN_MONITOR',               'LOW',      'MEDIUM',   2, 3,  6.0,  3.0, 'AMBER',  'IMPROVING', DATE '2025-07-12', DATE '2026-08-31',   6000000.00,  8, 'ACT-3.2', 10),
    ('OD-DES-01',
     'OWD-requested ROB addition at Cuttack approach (pending VO-003) (RK-DES-02)',
     'OWD CE office directed addition of a 4-span ROB at Cuttack approach (Ch 27+200) over the Howrah-Chennai trunk railway line. Original BOQ has only an at-grade crossing. VO-003 submitted Dec 2025; under technical scrutiny by RDSO.',
     'SCHEDULE', 'OPEN_BEING_MANAGED',              'MEDIUM',   'HIGH',     3, 4, 12.0,  6.0, 'AMBER',  'WORSENING', DATE '2025-12-08', DATE '2026-09-30',  65000000.00, 45, 'ACT-6.3,ACT-6.4', 11),
    ('OD-MAT-02',
     'Sand mining ban in Athgarh quarry — alternate sourcing required (RK-MAT-03)',
     'Athgarh approved quarry served show-cause notice by district mining authority on 18 Nov 2025; operations suspended pending environmental compliance review. Alternate sand from Mahanadi-Banki source identified, but lead time +3 days.',
     'RESOURCE', 'OPEN_BEING_MANAGED',              'MEDIUM',   'MEDIUM',   3, 3,  9.0,  4.0, 'AMBER',  'IMPROVING', DATE '2025-11-25', DATE '2026-06-30',   8000000.00, 18, 'ACT-2.1,ACT-2.2', 12)
) AS r(code, title, description, category, status, probability, impact, impact_cost, impact_schedule, risk_score, residual_risk_score, rag, trend, identified_date, due_date, cost_impact_rupees, schedule_impact_days, affected_activities, sort_order)
CROSS JOIN (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001') AS p;

-- ─────────────────────── Risk Responses ───────────────────────
-- 2-4 mitigation responses per risk, derived from the DOCX framework's
-- Avoid/Mitigate/Transfer/Accept strategies. Status COMPLETED for closed
-- risks (with actual_date), IN_PROGRESS or PLANNED for open ones.

INSERT INTO risk.risk_responses (
    id, created_at, updated_at, version,
    risk_id, response_type, description,
    responsible_id, planned_date, actual_date,
    estimated_cost, actual_cost, status
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    rk.id, rsp.response_type, rsp.description,
    NULL, rsp.planned_date, rsp.actual_date,
    rsp.estimated_cost, rsp.actual_cost, rsp.status
FROM (VALUES
    -- OD-LA-01 (CRITICAL — Cuttack acquisition)
    ('OD-LA-01', 'AVOID',     'Engage Cuttack District Collector for expedited LA proceedings under RFCTLARR Act 2013 with Section 3A notifications.',                        DATE '2025-04-01', NULL,                 1500000.00,  800000.00, 'IN_PROGRESS'),
    ('OD-LA-01', 'MITIGATE',  'Re-sequence construction to work in cleared stretches (Ch 0-22) first while LA disputes resolve. Maintain alternate work fronts.',          DATE '2025-04-15', DATE '2025-05-10',    3000000.00, 3120000.00, 'COMPLETED'),
    ('OD-LA-01', 'TRANSFER',  'Land acquisition is OWD-borne under EPC contract. Claim EOT under Clause 40 for delays attributable to OWD obligations.',                   DATE '2025-06-01', DATE '2025-08-15',          0.00,       0.00, 'COMPLETED'),
    ('OD-LA-01', 'MITIGATE','Fallback alignment via NH-16 service road for ROB approach if court order remains pending past Sep 2026 — design ready, needs OWD nod.',  DATE '2026-08-01', NULL,                 8500000.00,       0.00, 'PLANNED'),
    -- OD-FIN-01 (HIGH — payment cycle)
    ('OD-FIN-01', 'MITIGATE', 'Implement e-measurement and e-billing to reduce payment cycle time. Maintain 3-month working-capital reserve at MEIL.',                     DATE '2025-05-15', DATE '2025-07-01',    1200000.00, 1180000.00, 'COMPLETED'),
    ('OD-FIN-01', 'TRANSFER', 'Direct payment arrangement with critical subcontractors (bitumen + steel) under contract Clause 14.4 step-in rights.',                      DATE '2025-08-01', NULL,                       0.00,       0.00, 'IN_PROGRESS'),
    ('OD-FIN-01', 'ACCEPT',   'Absorb interest cost on stretched WC; price-variation invocation provides partial offset on quarterly indices.',                            DATE '2025-09-01', NULL,                 4500000.00, 2800000.00, 'IN_PROGRESS'),
    -- OD-MW-01 (CLOSED — Cyclone Dana)
    ('OD-MW-01', 'ACCEPT',    'Force Majeure notice issued under Clause 19.2 within 14 days of cyclone landfall; documented damage assessment.',                            DATE '2024-11-05', DATE '2024-11-04',          0.00,       0.00, 'COMPLETED'),
    ('OD-MW-01', 'TRANSFER',  'CAR insurance claim filed for embankment material damage (₹12M); 95% claim approved by ICICI-Lombard.',                                     DATE '2024-12-01', DATE '2024-12-08',          0.00,       0.00, 'COMPLETED'),
    ('OD-MW-01', 'MITIGATE',  'VO-002 raised for full embankment reinstatement Ch 13+800-14+600 (₹18M); approved 2024-12-10; works completed by Dec 2024.',                DATE '2024-12-15', DATE '2024-12-29',   18000000.00,18450000.00, 'COMPLETED'),
    -- OD-MAT-01 (HIGH — Bitumen supply)
    ('OD-MAT-01', 'MITIGATE', 'Quarterly forward contract with IOC for VG-30 ex-Paradip with 30-day buffer stock at MEIL site.',                                            DATE '2026-02-15', DATE '2026-03-12',    8000000.00, 8120000.00, 'COMPLETED'),
    ('OD-MAT-01', 'AVOID',    'Pre-position 240 MT inventory before Aug 2026 turnaround starts. Tank capacity at site sufficient for 35 days.',                            DATE '2026-06-01', NULL,                12000000.00,       0.00, 'PLANNED'),
    ('OD-MAT-01', 'TRANSFER', 'MoRTH price-escalation formula invocation for bitumen variance > 5% from base month.',                                                       DATE '2026-09-01', NULL,                       0.00,       0.00, 'PLANNED'),
    -- OD-ENV-01 (CRITICAL — Wildlife NOC)
    ('OD-ENV-01', 'AVOID',    'Engage MoEFCC-empanelled EIA consultant (WAPCOS) for revised wildlife management plan with elephant underpasses every 1 km in buffer zone.', DATE '2024-10-15', DATE '2024-12-20',    4500000.00, 4720000.00, 'COMPLETED'),
    ('OD-ENV-01', 'MITIGATE', 'On-site Environment & Social Management Unit established with dedicated Environment Officer; monthly EMP audits + quarterly DEIAA reports.',  DATE '2024-12-01', DATE '2025-01-15',    1800000.00, 1850000.00, 'COMPLETED'),
    ('OD-ENV-01', 'TRANSFER', 'Forest clearance condition is OWD obligation; claim EOT and additional costs for delays attributable to NOC pendency.',                       DATE '2025-06-01', NULL,                       0.00,       0.00, 'IN_PROGRESS'),
    -- OD-UTIL-01 (HIGH — OPTCL line)
    ('OD-UTIL-01', 'TRANSFER', 'Utility shifting included as OWD-borne obligation. Tripartite agreement with OPTCL for line conversion + outage scheduling.',                DATE '2025-03-15', DATE '2025-05-22',          0.00,       0.00, 'COMPLETED'),
    ('OD-UTIL-01', 'MITIGATE', 'Re-sequence to complete adjacent earthwork and sub-base before OPTCL outage; minimise outage window to 8 hrs.',                              DATE '2025-09-01', DATE '2025-10-12',          0.00,       0.00, 'COMPLETED'),
    ('OD-UTIL-01', 'MITIGATE','Activate temporary 33 kV bypass fed from Bhubaneswar-East substation if 220 kV outage exceeds 12 hrs. Standby diesel-genset 250 kVA.',     DATE '2026-04-01', NULL,                 1500000.00,       0.00, 'PLANNED'),
    -- OD-GEO-01 (MEDIUM — Soft clay)
    ('OD-GEO-01', 'MITIGATE', 'Lime + fly-ash sub-grade stabilisation (5% by weight) + geotextile separation layer 200 gsm. Design revision per VO-001.',                    DATE '2025-01-15', DATE '2025-02-28',   14000000.00,14250000.00, 'COMPLETED'),
    ('OD-GEO-01', 'MITIGATE', 'Settlement monitoring instrumentation (8 inclinometers + piezometers) installed Ch 22+400, 23+100, 23+800 — monthly readings.',               DATE '2025-03-01', DATE '2025-03-22',     800000.00,  815000.00, 'COMPLETED'),
    ('OD-GEO-01', 'AVOID',    'Pre-loading sub-grade for 60 days before WMM placement to dissipate excess pore water pressure in soft clay zone.',                           DATE '2025-04-01', DATE '2025-06-05',          0.00,       0.00, 'COMPLETED'),
    -- OD-LO-01 (MEDIUM — Local protest)
    ('OD-LO-01', 'AVOID',     'Community Liaison Unit established with local representative as point of contact. Monthly engagement with Khurda panchayat.',                  DATE '2025-08-25', DATE '2025-09-10',     500000.00,  520000.00, 'COMPLETED'),
    ('OD-LO-01', 'MITIGATE',  'Tender enhanced compensation package per RFCTLARR provisions; OWD legal counsel reviewing additional benefit-share.',                          DATE '2025-10-01', NULL,                 4500000.00, 1800000.00, 'IN_PROGRESS'),
    -- OD-LAB-01 (CLOSED — Paver operator)
    ('OD-LAB-01', 'MITIGATE', 'Empanel labour contractor from Coimbatore (TN) — 6-operator pool with VG-30 paver experience. ESIC/PF + camp facilities + DBT wages.',         DATE '2025-11-05', DATE '2025-11-28',    1200000.00, 1245000.00, 'COMPLETED'),
    ('OD-LAB-01', 'TRANSFER', 'Liquidated damages waiver claim for the 14-day productivity loss during ramp-up — NHAI/OWD precedent applied.',                                DATE '2025-12-15', DATE '2026-01-15',          0.00,       0.00, 'COMPLETED'),
    -- OD-HSE-01 (LOW — Humidity)
    ('OD-HSE-01', 'MITIGATE', 'Mix design revised: anti-stripping additive ratio increased from 0.3% to 0.5%; PMC approved per IRC SP-53 supplement.',                        DATE '2025-08-01', DATE '2025-08-22',     350000.00,  362000.00, 'COMPLETED'),
    ('OD-HSE-01', 'MITIGATE', 'Shift BC paving to 4 AM-9 AM during high-humidity months; mat temperature monitoring at 4 stations on the rolling pass.',                       DATE '2025-09-01', NULL,                       0.00,       0.00, 'IN_PROGRESS'),
    -- OD-DES-01 (HIGH — ROB addition)
    ('OD-DES-01', 'TRANSFER', 'Price all OWD-directed scope additions as Variation Orders per Clause 13. VO-003 submitted Dec 2025 (₹65M, 45-day impact).',                  DATE '2025-12-15', DATE '2025-12-22',          0.00,       0.00, 'COMPLETED'),
    ('OD-DES-01', 'MITIGATE', 'Parallel design + RDSO scrutiny via empanelled bridge consultant. Mock-up review with OWD CE office and RVNL Bhubaneswar team.',                DATE '2026-01-15', NULL,                 2200000.00, 1100000.00, 'IN_PROGRESS'),
    ('OD-DES-01', 'MITIGATE','Fallback to upgraded at-grade signalised crossing if RDSO approval slips beyond Sep 2026; partial scope acceptance.',                         DATE '2026-09-01', NULL,                12500000.00,       0.00, 'PLANNED'),
    -- OD-MAT-02 (MEDIUM — Sand quarry)
    ('OD-MAT-02', 'AVOID',    'Empanel two alternate sand sources (Banki + Tigiria); PMC approval received 28 Nov 2025 within 10 days of Athgarh suspension.',                DATE '2025-11-25', DATE '2025-12-08',    1200000.00, 1220000.00, 'COMPLETED'),
    ('OD-MAT-02', 'MITIGATE', 'Increase manufactured sand (M-sand) substitution from 20% to 35% in WMM and concrete mix per IS 383:2016 Cl. 5.4.',                            DATE '2026-01-15', NULL,                 1800000.00,  900000.00, 'IN_PROGRESS')
) AS rsp(risk_code, response_type, description, planned_date, actual_date, estimated_cost, actual_cost, status)
JOIN risk.risks rk ON rk.code = rsp.risk_code
 AND rk.project_id = (SELECT id FROM project.projects WHERE code = 'OWD/SH10/OD/2025/001');
