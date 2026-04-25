package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.ProjectCategory;
import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.RiskCategory;
import com.bipros.risk.domain.model.RiskTemplate;
import com.bipros.risk.domain.repository.RiskTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Seeds the system-default Risk Library — common risks for road projects, general
 * construction, refinery / oil-and-gas works, and a generic any-project set. Project
 * Managers pull from this library via the "Add from Library" flow on the risk register
 * so they don't start with a blank list and forget the well-known categories.
 *
 * <p>Idempotent: each row is upserted by {@code code}. Seeded rows carry
 * {@code systemDefault=true}; the service blocks deletion and {@code code}/{@code
 * industry} mutation on these rows.
 *
 * <p>Runs at {@code @Order(60)} — after {@link ResourceTypeDefSeeder} (50) and well
 * before the IC-PMS data loaders (101+) so any future seeders that copy library risks
 * onto demo projects will find them.
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class RiskTemplateSeeder implements CommandLineRunner {

    private final RiskTemplateRepository repository;

    private static final Set<String> ROAD_CATEGORIES = setOf(
        ProjectCategory.HIGHWAY,
        ProjectCategory.EXPRESSWAY,
        ProjectCategory.STATE_HIGHWAY,
        ProjectCategory.RURAL_ROAD,
        ProjectCategory.URBAN_ROAD);

    private static final Set<String> ALL_PROJECT_CATEGORIES = setOf(
        ProjectCategory.HIGHWAY,
        ProjectCategory.EXPRESSWAY,
        ProjectCategory.STATE_HIGHWAY,
        ProjectCategory.RURAL_ROAD,
        ProjectCategory.URBAN_ROAD,
        ProjectCategory.OTHER);

    @Override
    @Transactional
    public void run(String... args) {
        int inserted = 0;

        // ── ROAD ────────────────────────────────────────────────────────────────
        inserted += upsert("ROAD-LAND-001", "Land acquisition delays",
            "3A/3D notification or compensation negotiations slip, blocking handover of stretches.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.LAND_ACQUISITION,
            4, 3, 5, "Phase scope around acquired stretches; engage CALA early; track 3D-published vs handed-over.",
            10);
        inserted += upsert("ROAD-FOREST-002", "Forest / wildlife clearance hold-up",
            "Stage-I or Stage-II forest clearance pending; wildlife board NOC delays alignment fixes.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.FOREST_CLEARANCE,
            3, 2, 5, "File parallel applications; design alternates avoiding sensitive zones.",
            20);
        inserted += upsert("ROAD-UTIL-003", "Utility shifting (water / power / telecom)",
            "Existing utilities along RoW need relocation by owning agencies — cost and timing rest with them.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.UTILITY_SHIFTING,
            4, 3, 4, "Joint inspection + signed shifting plan + escrow with utility owners.",
            30);
        inserted += upsert("ROAD-MONSOON-004", "Monsoon impact on earthworks",
            "Heavy rains pause embankment / GSB / bituminous works for several weeks.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.MONSOON_IMPACT,
            5, 2, 4, "Front-load earthworks pre-monsoon; protect cut faces; plan indoor activities for July–Sept.",
            40);
        inserted += upsert("ROAD-GEO-005", "Adverse geotechnical / soft-soil conditions",
            "Sub-grade CBR or unexpected soft pockets force redesign of pavement or treatment.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.TECHNICAL,
            3, 4, 3, "Augment SI density; provision for ground improvement; track CBR test results.",
            50);
        inserted += upsert("ROAD-TRAFFIC-006", "Traffic-management failure during construction",
            "Inadequate diversions cause accidents, public complaints, or stop-work orders.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.EXTERNAL,
            3, 2, 2, "Approved TMP per IRC SP-55; daily safety walks; police liaison.",
            60);
        inserted += upsert("ROAD-QUARRY-007", "Quarry / aggregate availability",
            "Permitted quarries run dry or get banned mid-execution, raising lead and cost.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.RESOURCE,
            3, 4, 3, "Tie up multiple quarries; track approved-source register; pre-test reserves.",
            70);
        inserted += upsert("ROAD-CONTRACTOR-008", "Contractor financial distress",
            "Contractor cash-flow breaks lead to slow payments to subs, demob risk, possible NPA.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.CONTRACTOR_FINANCIAL,
            2, 5, 5, "Independent bank-statement review; back-to-back direct payment to critical vendors.",
            80);
        inserted += upsert("ROAD-DESIGN-009", "Design changes / scope creep mid-execution",
            "Authority directs structure / alignment changes after award.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.PROJECT_MANAGEMENT,
            3, 4, 4, "Strict change-control board; quantify impact in TEV before agreeing.",
            90);
        inserted += upsert("ROAD-ROW-010", "Right-of-way disputes / encroachments",
            "Local resistance, encroachments, or boundary disputes block possession of land.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.STATUTORY_CLEARANCE,
            4, 3, 4, "Joint demarcation with revenue authority; rehabilitation package per RFCTLARR.",
            100);
        inserted += upsert("ROAD-PERMIT-011", "Statutory clearance delays (Env / PWD / MoRTH)",
            "EC / consent-to-establish / NHAI sub-approvals run beyond planned window.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.STATUTORY_CLEARANCE,
            3, 2, 4, "Clearance tracker with pre-emptive follow-up; pre-construction clearances bundled.",
            110);

        // ── ROAD — Risk Master DOCX framework (sections 4.1 - 4.11) ───────────
        // Sourced from "Risk Master Metadata - Road Construction Project.docx" supplied 2026-04
        // Land Acquisition (4.1)
        inserted += upsert("ROAD-LA-12", "Delayed Right-of-Way handover (RK-LA-01)",
            "Three private land parcels remain under acquisition dispute, blocking embankment start on the most heavily-loaded segment.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.LAND_ACQUISITION,
            4, 5, 5, "Engage District Collector for expedited LA proceedings under RFCTLARR Act 2013 with Section 3A notifications. Re-sequence to cleared stretches first; invoke EOT.",
            120);
        inserted += upsert("ROAD-LA-13", "Encroachment by structures / Jhuggi settlements (RK-LA-02)",
            "ROW encroachments by informal settlements blocking 30-60 day work fronts.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.LAND_ACQUISITION,
            3, 4, 4, "RFCTLARR-compliant rehabilitation package; community engagement; Police-NHAI joint demolition orders for non-compliant structures.",
            121);
        inserted += upsert("ROAD-UTIL-14", "Underground utility conflict — OFC/Pipeline/HT cable (RK-LA-03)",
            "Telecom OFC, gas pipeline, or HT power cable crossings discovered during excavation.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.UTILITY_SHIFTING,
            4, 4, 4, "Drone-based ROW + utility survey pre-construction; advance shifting agreements with BSNL/PGCIL/GAIL; utility shifting as NHAI-borne contractual obligation.",
            122);
        // Geotechnical (4.2)
        inserted += upsert("ROAD-GEO-15", "Unexpected rock formation in cut sections (RK-GEO-01)",
            "Hard rock at less than 1.5m depth, requiring blasting permits and stockpiled lime/fly ash.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.TECHNICAL,
            3, 4, 4, "GI boreholes at 500m intervals; seismic refraction surveys; advance blasting permits; price escalation provisions for excess rock.",
            130);
        inserted += upsert("ROAD-GEO-16", "Black cotton / expansive soil subgrade (RK-GEO-02)",
            "CBR < 2% in subgrade zone necessitates lime/cement stabilisation or sub-grade replacement.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.TECHNICAL,
            3, 3, 3, "Pre-construction CBR testing every 250m; lime/cement stabilisation provision in BOQ; geosynthetic separation layers.",
            131);
        inserted += upsert("ROAD-GEO-17", "Groundwater ingress in embankment / foundation zone (RK-GEO-03)",
            "Groundwater above 2m below FGL in embankment zone; affects compaction and pier foundations.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.TECHNICAL,
            2, 4, 4, "French drains; interceptor drains; blanket drainage layers; groundwater-level monitoring wells at 100m intervals.",
            132);
        // Monsoon / Weather (4.3)
        inserted += upsert("ROAD-MW-18", "Monsoon flooding of work sites / embankments (RK-MW-01)",
            "IMD orange/red rainfall alert; flash floods damaging in-progress embankments.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.MONSOON_IMPACT,
            4, 4, 4, "Front-load earthwork to complete by end-June; pre-position dewatering pumps (2 per work front); berms + silt traps; IMD 5-day advisory subscription.",
            140);
        inserted += upsert("ROAD-MW-19", "Extreme heat (>45°C) suspending bituminous paving (RK-MW-02)",
            "Mat temperature drops below acceptance window during peak summer.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.NATURAL_HAZARD,
            3, 3, 3, "Shift bituminous paving to 4 AM-9 AM during summer; use VG-40 grade bitumen; pre-mobilise night-shift crews.",
            141);
        inserted += upsert("ROAD-MW-20", "Dust storms (Andhi) disrupting operations (RK-MW-03)",
            "Visibility-related work suspension; dust contamination of bituminous mat.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.NATURAL_HAZARD,
            3, 2, 2, "Daily IMD weather log; site dust-suppression water-tankers; tarp protection of paved sections.",
            142);
        // Materials / price (4.4)
        inserted += upsert("ROAD-MAT-21", "Bitumen price escalation / shortage (RK-MAT-01)",
            "Bitumen MoM variance > 8% on IOC schedule; refinery turnaround cycles.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.MARKET_PRICE,
            3, 4, 3, "Quarterly forward contracts with IOC/HPCL with price-lock; 30-day buffer stock; MoRTH price-escalation formula > 5% variance.",
            150);
        inserted += upsert("ROAD-MAT-22", "Steel / reinforcement non-availability for bridges (RK-MAT-02)",
            "Steel index > 10% above base month; bridge superstructure work stoppage risk.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.MARKET_PRICE,
            2, 4, 3, "Pre-mobilisation order placement; 60-day stock at site; alternate sourcing approvals from PMC.",
            151);
        inserted += upsert("ROAD-MAT-23", "Quarry closure / aggregate shortage (RK-MAT-03)",
            "Mining authority show-cause to permitted quarry; aggregate lead time spike.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.RESOURCE,
            3, 3, 3, "Two approved quarry sources per category; advance PMC approval; RAP up to 20% in bituminous base course.",
            152);
        // Labour / equipment (4.5)
        inserted += upsert("ROAD-LAB-24", "Labour shortage / seasonal migration (RK-LAB-01)",
            "Daily labour attendance below 75% of planned for 3+ consecutive days; festival/harvest migration.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.RESOURCE,
            3, 3, 3, "Two registered labour contractors from multiple states; dignified camp facilities; ESIC/PF coverage; transparent DBT wages.",
            160);
        inserted += upsert("ROAD-LAB-25", "Labour unrest / strike (RK-LAB-02)",
            "Written complaint to Labour Commissioner; work stoppage; arbitration risk.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.EXTERNAL,
            2, 4, 3, "Grievance Redressal Mechanism with 24-hour hotline; fortnightly labour welfare meetings; timely DBT wage disbursement.",
            161);
        inserted += upsert("ROAD-LAB-26", "Critical equipment breakdown — Grader/Paver/Roller (RK-LAB-03)",
            "Equipment utilisation rate below 60% for critical plant; lost production days.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.RESOURCE,
            3, 3, 3, "OEM AMC for critical equipment; 15% spare-parts pre-positioning; empanel 2 local hire agencies; CAR/Equipment Breakdown insurance.",
            162);
        // Environmental / forest (4.6)
        inserted += upsert("ROAD-ENV-27", "Delay in forest / wildlife clearance (RK-ENV-01)",
            "Stage-I/Stage-II forest clearance pending; NGT petition filed.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.FOREST_CLEARANCE,
            3, 5, 5, "Forest diversion proposal 24 months before mobilisation; MoEFCC-empanelled EIA consultant; alternates avoiding sensitive zones.",
            170);
        inserted += upsert("ROAD-ENV-28", "Non-compliance with EMP / ESMP conditions (RK-ENV-02)",
            "MoEFCC inspection notice; failed EMP audit triggering work stop.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.STATUTORY_CLEARANCE,
            2, 4, 3, "On-site Environment & Social Management Unit with dedicated EO; monthly EMP audits; quarterly DEIAA reports.",
            171);
        inserted += upsert("ROAD-ENV-29", "Discovery of archaeological / heritage assets in excavation (RK-ENV-03)",
            "ASI-protected artefacts found in excavation; alignment review and stop-work risk.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.TECHNICAL,
            1, 5, 5, "Preliminary archaeological survey with ASI; contingency clause in schedule for heritage zones.",
            172);
        // Financial (4.7)
        inserted += upsert("ROAD-FIN-30", "Contractor cash-flow stress / delayed Authority payments (RK-FIN-01)",
            "IPC payment pending beyond 35 days; subcontractor payment claims piling up.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.CONTRACTOR_FINANCIAL,
            3, 4, 3, "28-day IPC processing per contract; e-measurement and e-billing; 3-month working-capital reserve; invoke price-variation indices.",
            180);
        inserted += upsert("ROAD-FIN-31", "Contractor financial default / insolvency (RK-FIN-02)",
            "Contractor MIS shows < 30-day working capital; PBG invocation imminent.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.CONTRACTOR_FINANCIAL,
            2, 5, 5, "Performance Bank Guarantee 10% with step-in rights; quarterly bank-statement review; direct payment to critical subs; termination + retender.",
            181);
        inserted += upsert("ROAD-FIN-32", "Subcontractor payment default / walk-off (RK-FIN-03)",
            "Specialist sub demobilises mid-execution; rework + replacement lead time.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.RESOURCE,
            3, 3, 3, "DAB mechanism in subcontracts; 21-day formal-notice resolution window; alternate sub list pre-empanelled.",
            182);
        // Design change (4.8)
        inserted += upsert("ROAD-DES-33", "Mid-project geometric design change (IRC update) (RK-DES-01)",
            "IRC/MoRTH circular received during execution mandates design change.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.TECHNICAL,
            2, 3, 3, "Freeze design at approved DPR with NHAI concurrence; Change Control Board; price all NHAI-directed scope additions as VOs.",
            190);
        inserted += upsert("ROAD-DES-34", "Mid-project grade-separator / flyover addition (RK-DES-02)",
            "NHAI directs flyover or ROB addition mid-execution; major schedule + cost impact.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.SCHEDULE,
            2, 4, 5, "VO process pre-approved; design contingency in baseline schedule (10% float for design activities); digital As-Built model updated monthly.",
            191);
        // HSE (4.9)
        inserted += upsert("ROAD-HSE-35", "Fatal construction accident (RK-HSE-01)",
            "Lost-time fatal incident triggering work stoppage, legal action, contractor compensation.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.HEALTH_SAFETY,
            2, 5, 4, "ISO 45001 OHSMS; daily toolbox talks; PPE enforcement; HSE Officer per 5km active stretch; monthly third-party HSE audits.",
            200);
        inserted += upsert("ROAD-HSE-36", "Third-party road accident in work zone (RK-HSE-02)",
            "Public-vehicle accident at work-zone diversion; police action and PIL risk.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.HEALTH_SAFETY,
            3, 4, 3, "IRC:SP:55 traffic management plan at all work zones; solar VMS; trained traffic marshals; barricades + reflectors.",
            201);
        inserted += upsert("ROAD-HSE-37", "Hazardous material spill — bitumen / fuel (RK-HSE-03)",
            "Hot bitumen / diesel spill on site; pollution control board notice risk.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.HEALTH_SAFETY,
            2, 3, 2, "Bunded fuel storage; spill kits at every plant; bituminous-mat handling SOP; pollution-control board notification within 24 hrs.",
            202);
        // Law & Order (4.10)
        inserted += upsert("ROAD-LO-38", "Community agitation / dharna against land acquisition (RK-LO-01)",
            "Local newspaper reports of opposition; community dharna at site.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.EXTERNAL,
            3, 4, 3, "Community Liaison Unit; village/panchayat engagement meetings pre-commencement; documented LA compliance.",
            210);
        inserted += upsert("ROAD-LO-39", "Court injunction / NGT stay order (RK-LO-02)",
            "PIL filed; NGT stay or court injunction halts work for months.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.GEOPOLITICAL,
            2, 5, 5, "Empanelled legal counsel for pre-emptive monitoring; RTI response within 30 days; LA compliance documentation; EOT claim.",
            211);
        // Force majeure (4.11)
        inserted += upsert("ROAD-FM-40", "Earthquake / natural disaster (RK-FM-01)",
            "Seismic event; cyclone landfall; flood damage to site infrastructure.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.NATURAL_HAZARD,
            1, 5, 4, "Force Majeure notice within 14 days per Clause 19.2; CAR insurance claim; Clause 40 EOT; emergency response plan with NDRF/SDRF.",
            220);
        inserted += upsert("ROAD-FM-41", "Epidemic / pandemic (COVID-type) (RK-FM-02)",
            "National health emergency requiring site demobilisation.",
            Industry.ROAD, ROAD_CATEGORIES, RiskCategory.GEOPOLITICAL,
            1, 5, 4, "BCP for critical supply chains; force-majeure clause coverage; minimum-staff site protocol; vaccinated workforce policy.",
            221);

        // ── CONSTRUCTION (general / building / bridge) ─────────────────────────
        inserted += upsert("CON-SAFETY-001", "Site safety incident / lost-time injury",
            "Fall, struck-by, or hot-work injury triggers stop-work and authority scrutiny.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.QUALITY,
            2, 3, 3, "Daily toolbox talks; PPE audit; hazard register reviewed weekly.",
            210);
        inserted += upsert("CON-SUB-002", "Subcontractor non-performance",
            "Specialist sub fails to mobilise or deliver to spec.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.RESOURCE,
            3, 3, 4, "Performance bond; weekly look-ahead reviews; backup sub list.",
            220);
        inserted += upsert("CON-PRICE-003", "Material price volatility (steel / cement / bitumen)",
            "Index movement outside contractual ceilings erodes margin or triggers price-variation claims.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.MARKET_PRICE,
            4, 4, 2, "Lock orders early; hedge bitumen via supplier MoUs; track WPI vs PVA cap.",
            230);
        inserted += upsert("CON-WEATHER-004", "Adverse weather (cyclone / heavy rain / heat)",
            "Weather window shrinks beyond plan; protective measures and re-work add cost.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.NATURAL_HAZARD,
            3, 2, 3, "Weather-window matrix per activity; tarp/sheet stock; heatwave shift planning.",
            240);
        inserted += upsert("CON-LABOUR-005", "Labour shortage / unrest",
            "Skilled crew migration, festivals, or local agitation reduces productivity.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.RESOURCE,
            3, 3, 4, "Labour camps + welfare; staggered leave plan; engage local representatives.",
            250);
        inserted += upsert("CON-DESIGN-006", "Design clash detected late (BIM / coordination)",
            "MEP-structural-architectural clashes surface during execution requiring rework.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.TECHNICAL,
            3, 3, 3, "BIM clash-detection at LOD-300; weekly coordination meetings.",
            260);
        inserted += upsert("CON-EQUIP-007", "Critical equipment breakdown",
            "Tower crane / batching plant / paver outage halts the critical path.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.RESOURCE,
            2, 2, 4, "Spare-parts stock; AMC with 24h response; standby unit for highest-criticality plant.",
            270);
        inserted += upsert("CON-PERMIT-008", "Local-body / fire / occupancy permit delay",
            "BBMP / municipal / fire NOC slips, blocking handover or commissioning.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.STATUTORY_CLEARANCE,
            3, 2, 3, "Single PMO owner per permit; pre-application reviews; track conditions vs design.",
            280);
        inserted += upsert("CON-QUALITY-009", "QA/QC rejection requiring rework",
            "Concrete cube / weld test / surface finish fails acceptance; rework cost + time.",
            Industry.CONSTRUCTION_GENERAL, Set.of(), RiskCategory.QUALITY,
            3, 3, 3, "ITP enforcement; third-party labs for critical pours; first-time-right metric.",
            290);

        // ── REFINERY / OIL_GAS (drawn from docs/iocl-panipat-wo.md §9) ────────
        inserted += upsert("OG-DISMANTLE-001", "Phased dismantling / handover slippage",
            "Sequential handover of equipment from operations runs late, compressing execution window.",
            Industry.OIL_GAS, Set.of(), RiskCategory.SCHEDULE,
            4, 3, 5, "Joint sequencing plan signed by ops; daily handover stand-up.",
            310);
        inserted += upsert("OG-WASTAGE-002", "Wastage cap exceedance (20% penalty)",
            "Cut-and-fit losses on free-issue insulation / piping cross the contractual cap.",
            Industry.OIL_GAS, Set.of(), RiskCategory.COST,
            3, 4, 2, "Pre-fab where possible; cut-list optimisation; daily wastage MIS.",
            320);
        inserted += upsert("OG-INSUL-003", "Insulation disposal compliance failure",
            "Old insulation contains regulated substances; non-compliant disposal triggers penalties.",
            Industry.OIL_GAS, Set.of(), RiskCategory.STATUTORY_CLEARANCE,
            2, 3, 2, "Authorised TSDF tie-up; manifest tracking per consignment.",
            330);
        inserted += upsert("OG-FIM-004", "Free-issue material damage / re-procurement",
            "Damage to client-supplied materials causes re-procurement risk owned by contractor.",
            Industry.OIL_GAS, Set.of(), RiskCategory.COST,
            3, 4, 3, "Receipt inspection with photos; tagged stowage; insurance for FIM stock.",
            340);
        inserted += upsert("OG-HYDRO-005", "Hydrotest / dye-penetration test failure",
            "Pressure-test failure forces weld redo and retesting on the critical path.",
            Industry.OIL_GAS, Set.of(), RiskCategory.TECHNICAL,
            3, 3, 3, "Welder qualification per spec; in-process NDT; mock-up tests.",
            350);
        inserted += upsert("OG-CLEAR-006", "IOCL three-stage clearance hold-up",
            "Operations / maintenance / safety sign-offs delay restart of work zones.",
            Industry.OIL_GAS, Set.of(), RiskCategory.STATUTORY_CLEARANCE,
            3, 2, 4, "Joint clearance template; fixed daily slot for sign-offs.",
            360);
        inserted += upsert("OG-HOTWORK-007", "Hot-work permit delays in live refinery",
            "Permit issuance restricted to specific windows; concurrent-operation conflicts.",
            Industry.OIL_GAS, Set.of(), RiskCategory.EXTERNAL,
            4, 2, 4, "Daily permit plan synced with ops; alternates for cold-work activities.",
            370);
        inserted += upsert("OG-CONCURRENT-008", "Concurrent-operations clash with running plant",
            "Plant trips, vibration, or odour issues trigger work suspension by operations.",
            Industry.OIL_GAS, Set.of(), RiskCategory.EXTERNAL,
            3, 3, 4, "Daily SimOps review; sealed scaffold near live equipment.",
            380);

        // ── GENERIC (apply to any project of any industry) ─────────────────────
        inserted += upsert("GEN-STAKE-001", "Stakeholder / community resistance",
            "Local groups oppose alignment, working hours, or compensation terms.",
            Industry.GENERIC, ALL_PROJECT_CATEGORIES, RiskCategory.EXTERNAL,
            2, 2, 3, "Stakeholder map; CSR engagement; documented grievance redress.",
            500);
        inserted += upsert("GEN-REGCHG-002", "Regulatory or tax-policy change",
            "GST / cess / labour-law amendments shift cost basis post-award.",
            Industry.GENERIC, ALL_PROJECT_CATEGORIES, RiskCategory.GEOPOLITICAL,
            2, 3, 2, "Change-in-law clause coverage; tax-watch advisory feed.",
            510);
        inserted += upsert("GEN-FX-003", "Forex / currency fluctuation (imported items)",
            "Imported equipment or specialty materials swing in INR cost.",
            Industry.GENERIC, ALL_PROJECT_CATEGORIES, RiskCategory.MARKET_PRICE,
            3, 3, 1, "Forward contracts on FX; limit exposure to <30% of contract value.",
            520);
        inserted += upsert("GEN-FORCE-004", "Force majeure (pandemic / strike / civil unrest)",
            "External disruption suspends work — excused but still erodes schedule float.",
            Industry.GENERIC, ALL_PROJECT_CATEGORIES, RiskCategory.EXTERNAL,
            1, 4, 4, "Force-majeure clause; BCP for critical supply chains.",
            530);
        inserted += upsert("GEN-CYBER-005", "Cybersecurity / data-breach incident",
            "Project-management or document-management system breach exposes commercials.",
            Industry.GENERIC, ALL_PROJECT_CATEGORIES, RiskCategory.TECHNOLOGY,
            2, 3, 2, "MFA on all PM tools; quarterly access review; tested incident-response.",
            540);
        inserted += upsert("GEN-PM-006", "Project-management / governance gaps",
            "Missing decisions, late MIS, or absent risk reviews compound into slippage.",
            Industry.GENERIC, ALL_PROJECT_CATEGORIES, RiskCategory.PROJECT_MANAGEMENT,
            3, 3, 3, "Weekly governance forum with stage-gate reviews; RAID log discipline.",
            550);
        inserted += upsert("GEN-CHANGE-007", "Late client-driven change orders",
            "Client requests scope changes after critical-path work has started.",
            Industry.GENERIC, ALL_PROJECT_CATEGORIES, RiskCategory.PROJECT_MANAGEMENT,
            4, 3, 3, "Documented change-request workflow with TEV; freeze date for design.",
            560);

        if (inserted > 0) {
            log.info("[RiskTemplateSeeder] inserted {} system-default risk template(s)", inserted);
        }
    }

    private int upsert(
        String code,
        String title,
        String description,
        Industry industry,
        Set<String> applicableProjectCategories,
        RiskCategory category,
        int defaultProbability,
        int defaultImpactCost,
        int defaultImpactSchedule,
        String mitigationGuidance,
        int sortOrder) {
        if (repository.findByCode(code).isPresent()) {
            return 0;
        }
        RiskTemplate template = RiskTemplate.builder()
            .code(code)
            .title(title)
            .description(description)
            .industry(industry)
            .applicableProjectCategories(new HashSet<>(applicableProjectCategories))
            .category(category)
            .defaultProbability(defaultProbability)
            .defaultImpactCost(defaultImpactCost)
            .defaultImpactSchedule(defaultImpactSchedule)
            .mitigationGuidance(mitigationGuidance)
            .isOpportunity(Boolean.FALSE)
            .sortOrder(sortOrder)
            .active(Boolean.TRUE)
            .systemDefault(Boolean.TRUE)
            .build();
        repository.save(template);
        return 1;
    }

    private static Set<String> setOf(ProjectCategory... categories) {
        return Stream.of(categories).map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
