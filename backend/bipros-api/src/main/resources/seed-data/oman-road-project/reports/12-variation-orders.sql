-- BNK reports/12-variation-orders.sql — Three VOs against main contract.
--
-- Plan §4.32:
--   VO-001 — Pier P3 soft-strata pile cap depth +180,000 OMR — APPROVED
--   VO-002 — OETC HT crossing underground relocation +320,000 OMR — RECOMMENDED (== "PENDING_APPROVAL" in plan terminology)
--   VO-003 — Additional median plantation +95,000 OMR — INITIATED (== "DRAFT")
--
-- VariationOrderStatus enum has only INITIATED|RECOMMENDED|APPROVED|REJECTED.
-- Plan terminology mapping:
--   "DRAFT"             → INITIATED
--   "PENDING_APPROVAL"  → RECOMMENDED
--   "APPROVED"          → APPROVED

INSERT INTO contract.variation_orders (
    id, created_at, updated_at, version,
    contract_id, vo_number, description, justification,
    vo_value, impact_on_budget, impact_on_schedule_days,
    status, approved_by, approved_at
)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
       c.id, v.vo_number, v.description, v.justification,
       v.vo_value, v.impact_on_budget, v.impact_on_schedule_days,
       v.status, v.approved_by, v.approved_at
FROM (VALUES
    ('VO-001',
     'Additional pile cap depth at Wadi Al Hattat bridge Pier P3 due to unanticipated soft strata encountered at 6 m depth.  Design revision: 4 m deeper pile cap, 8 additional bored cast-in-situ piles (1200 mm dia × 22 m), and peer review by Sultan Qaboos University Civil Engineering Faculty.',
     'Discovery during pier P3 excavation on 2025-04-08.  Geotechnical investigation per BS EN 1997 confirmed N-value < 6 till 11 m RL.  Peer review (SQU letter ref. CE/BNK/2025-018 dated 2025-06-22) recommends deeper founding.  MoTC RO Muscat approval per note MOTC/BNK/VO-001 dated 2025-08-30.',
     180000.000, 180000.000, 18, 'APPROVED',
     'Eng. Khalid Al-Saadi, MoTC Project Director', TIMESTAMP '2025-08-30 10:30:00+04:00'),

    ('VO-002',
     'OETC overhead 132 kV HT crossing at Ch 24+500 to be relocated underground per Oman Electricity Transmission Company directive.  Scope: 1.4 km underground 132 kV cable laying + two RMU substations + road crossings reinstatement.',
     'OETC communication ref. OETC/BNK/2026-005 dated 2026-01-22 mandating OHL-to-UG conversion to clear aerial obstruction for the widened 4-lane corridor.  Original BOQ did not include this scope.  MoTC funding confirmation pending Diwan approval.',
     320000.000, 320000.000, 25, 'RECOMMENDED',
     NULL, NULL),

    ('VO-003',
     'Additional median plantation along the central reservation — 800 additional Acacia / Ghaf seedlings, drip irrigation extension, and 18-month maintenance period.  Driven by Ministry of Environment & Climate Affairs request to enhance roadside greening.',
     'MECA letter dated 2026-04-10 requesting enhanced greening to mitigate dust and improve corridor aesthetics for tourism axis Muscat → Sohar.  Proposal under review by MoTC Quantity Surveyor; not yet recommended.',
      95000.000,  95000.000,  5, 'INITIATED',
     NULL, NULL)
) AS v(vo_number, description, justification, vo_value, impact_on_budget, impact_on_schedule_days, status, approved_by, approved_at)
CROSS JOIN (SELECT id FROM contract.contracts WHERE contract_number = 'BNK-MAIN-2024-001') AS c
WHERE NOT EXISTS (
    SELECT 1 FROM contract.variation_orders existing
    WHERE  existing.contract_id = c.id
      AND  existing.vo_number   = v.vo_number
);
