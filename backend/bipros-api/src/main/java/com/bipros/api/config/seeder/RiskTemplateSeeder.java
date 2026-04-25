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
