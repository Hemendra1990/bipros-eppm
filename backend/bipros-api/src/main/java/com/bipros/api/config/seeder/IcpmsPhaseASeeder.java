package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.model.OrganisationType;
import com.bipros.admin.domain.repository.OrganisationRepository;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarType;
import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.calendar.domain.repository.CalendarWorkWeekRepository;
import com.bipros.project.domain.model.AssetClass;
import com.bipros.project.domain.model.EpsNode;
import com.bipros.project.domain.model.ObsNode;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.model.WbsPhase;
import com.bipros.project.domain.model.WbsStatus;
import com.bipros.project.domain.model.WbsType;
import com.bipros.project.domain.repository.EpsNodeRepository;
import com.bipros.project.domain.repository.ObsNodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.security.domain.model.AuthMethod;
import com.bipros.security.domain.model.IcpmsModule;
import com.bipros.security.domain.model.ModuleAccessLevel;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserCorridorScope;
import com.bipros.security.domain.model.UserModuleAccess;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.RoleRepository;
import com.bipros.security.domain.repository.UserCorridorScopeRepository;
import com.bipros.security.domain.repository.UserModuleAccessRepository;
import com.bipros.security.domain.repository.UserRepository;
import com.bipros.security.domain.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * IC-PMS Phase A seeder — master data foundation for the Delhi-Mumbai Industrial Corridor (DMIC).
 *
 * <p>Seeds, idempotently via sentinel-row checks:
 * <ul>
 *   <li>15 organisations (Employer, SPVs, PMCs, EPC contractors, auditors)</li>
 *   <li>6 DMIC calendars with working-week patterns</li>
 *   <li>DMIC EPS + OBS hierarchy, master project, 35 WBS elements spanning 5 nodes</li>
 *   <li>20 users with designation/role/auth-methods + module access matrix + corridor scopes</li>
 * </ul>
 *
 * <p>Runs after {@code DataSeeder} (Order=100) so baseline roles/admin user exist.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(101)
@RequiredArgsConstructor
public class IcpmsPhaseASeeder implements CommandLineRunner {

    private final OrganisationRepository organisationRepository;
    private final CalendarRepository calendarRepository;
    private final CalendarWorkWeekRepository calendarWorkWeekRepository;
    private final EpsNodeRepository epsNodeRepository;
    private final ObsNodeRepository obsNodeRepository;
    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserModuleAccessRepository userModuleAccessRepository;
    private final UserCorridorScopeRepository userCorridorScopeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (organisationRepository.findByCode("NICDC").isPresent()) {
            log.info("[IC-PMS Phase A] sentinel 'NICDC' present, skipping");
            return;
        }
        log.info("[IC-PMS Phase A] seeding master data for DMIC programme…");

        Map<String, Organisation> orgs = seedOrganisations();
        Map<String, Calendar> calendars = seedCalendars();
        DmicHierarchy h = seedDmicHierarchy(orgs);
        Map<String, WbsNode> wbs = seedWbs(h, orgs);
        seedUsers(orgs, wbs);

        log.info("[IC-PMS Phase A] completed: {} orgs, {} calendars, {} WBS elements",
                orgs.size(), calendars.size(), wbs.size());
    }

    // ---------------------- Organisations ----------------------

    private Map<String, Organisation> seedOrganisations() {
        Map<String, Organisation> map = new HashMap<>();
        save(map, "NICDC", "National Industrial Corridor Development Corporation", "NICDC", OrganisationType.EMPLOYER, null);
        save(map, "DMICDC", "Delhi Mumbai Industrial Corridor Development Corp.", "DMICDC", OrganisationType.SPV, "NICDC");
        save(map, "DIICDC", "Dholera Industrial City Development Corp. (Gujarat SPV)", "DIICDC", OrganisationType.SPV, "DMICDC");
        save(map, "SIPCOT", "State Industries Promotion Corp. Tamil Nadu (Krishnagiri SPV)", "SIPCOT", OrganisationType.SPV, "DMICDC");
        save(map, "RIICO", "Rajasthan State Industrial Development & Investment Corp.", "RIICO", OrganisationType.SPV, "DMICDC");
        save(map, "AECOM-TYPSA", "AECOM-TYPSA Consortium (PMC)", "AECOM-TYPSA", OrganisationType.PMC, null);
        save(map, "EGIS-PMC", "Egis India PMC", "Egis", OrganisationType.PMC, null);
        save(map, "MOTT-MAC", "Mott MacDonald India PMC", "Mott MacDonald", OrganisationType.PMC, null);
        save(map, "LNT-IDPL", "L&T Infrastructure Development Projects Ltd.", "L&T IDPL", OrganisationType.EPC_CONTRACTOR, null);
        save(map, "TATA-PROJ", "Tata Projects Ltd.", "Tata Projects", OrganisationType.EPC_CONTRACTOR, null);
        save(map, "AFCONS", "Afcons Infrastructure Ltd.", "Afcons", OrganisationType.EPC_CONTRACTOR, null);
        save(map, "HCC", "Hindustan Construction Co. Ltd.", "HCC", OrganisationType.EPC_CONTRACTOR, null);
        save(map, "DILIP-BUILDCON", "Dilip Buildcon Ltd.", "Dilip Buildcon", OrganisationType.EPC_CONTRACTOR, null);
        save(map, "CAG", "Comptroller and Auditor General of India", "CAG", OrganisationType.GOVERNMENT_AUDITOR, null);
        save(map, "CVC", "Central Vigilance Commission", "CVC", OrganisationType.GOVERNMENT_AUDITOR, null);
        log.info("[IC-PMS Phase A] seeded {} organisations", map.size());
        return map;
    }

    private void save(Map<String, Organisation> map, String code, String name, String shortName,
                      OrganisationType type, String parentCode) {
        Organisation org = Organisation.builder()
                .code(code)
                .name(name)
                .shortName(shortName)
                .organisationType(type)
                .active(true)
                .build();
        if (parentCode != null && map.containsKey(parentCode)) {
            org.setParentOrganisationId(map.get(parentCode).getId());
        }
        map.put(code, organisationRepository.save(org));
    }

    // ---------------------- Calendars ----------------------

    private Map<String, Calendar> seedCalendars() {
        Map<String, Calendar> map = new HashMap<>();
        // Standard 6-day working week (typical Indian infrastructure contractor)
        map.put("DMIC-6day", saveCalendar("DMIC-6day", "DMIC six-day working week (Mon-Sat)",
                6, new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY}, 8.0));
        // 7-day (24x7 sites like rail electrification)
        map.put("DMIC-7day", saveCalendar("DMIC-7day", "DMIC seven-day construction week", 7,
                DayOfWeek.values(), 8.0));
        // Office (Mon-Fri)
        map.put("DMIC-OFFICE", saveCalendar("DMIC-OFFICE", "DMIC PMC office calendar (Mon-Fri)", 5,
                new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}, 8.0));
        // Night shift
        map.put("DMIC-NIGHT", saveCalendar("DMIC-NIGHT", "DMIC night-shift calendar", 6,
                new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY}, 10.0));
        // Monsoon restricted
        map.put("DMIC-MONSOON", saveCalendar("DMIC-MONSOON", "DMIC monsoon-restricted calendar (Jun-Sep)",
                4, new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY}, 6.0));
        // Pilot/demo sites
        map.put("DMIC-PILOT", saveCalendar("DMIC-PILOT", "DMIC pilot/demo site calendar",
                5, new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}, 8.0));
        log.info("[IC-PMS Phase A] seeded {} DMIC calendars", map.size());
        return map;
    }

    private Calendar saveCalendar(String name, String description, int daysPerWeek,
                                  DayOfWeek[] workingDays, double hoursPerDay) {
        Calendar cal = Calendar.builder()
                .code(name)
                .name(name)
                .description(description)
                .calendarType(CalendarType.GLOBAL)
                .isDefault(false)
                .standardWorkHoursPerDay(hoursPerDay)
                .standardWorkDaysPerWeek(daysPerWeek)
                .build();
        Calendar saved = calendarRepository.save(cal);
        Set<DayOfWeek> workingSet = Set.of(workingDays);
        for (DayOfWeek day : DayOfWeek.values()) {
            CalendarWorkWeek.CalendarWorkWeekBuilder b = CalendarWorkWeek.builder()
                    .calendarId(saved.getId())
                    .dayOfWeek(day);
            if (workingSet.contains(day)) {
                b.dayType(DayType.WORKING)
                        .startTime1(LocalTime.of(8, 0))
                        .endTime1(LocalTime.of(12, 0))
                        .startTime2(LocalTime.of(13, 0))
                        .endTime2(LocalTime.of(8 + (int) Math.round(hoursPerDay) + 1, 0))
                        .totalWorkHours(hoursPerDay);
            } else {
                b.dayType(DayType.NON_WORKING).totalWorkHours(0.0);
            }
            calendarWorkWeekRepository.save(b.build());
        }
        return saved;
    }

    // ---------------------- DMIC hierarchy ----------------------
    //
    // Corrected classification (GAP #3 / GAP #4):
    //   EPS tree — corridor LOCATIONS  (DMIC programme -> 5 geographic regions)
    //   OBS tree — ORGANISATION hierarchy (NICDC -> DMICDC -> SPVs/PMCs/EPCs/Auditors)
    // Projects/WBS nodes reference the geographic region via epsNodeId and the
    // responsible organisation via obsNodeId.

    private record DmicHierarchy(
            EpsNode programme,
            Map<String, EpsNode> geoNodes,
            Map<String, ObsNode> obsNodes,
            Project project) {}

    private DmicHierarchy seedDmicHierarchy(Map<String, Organisation> orgs) {
        // --- EPS: DMIC programme + 5 geographic corridor nodes ---
        EpsNode programme = new EpsNode();
        programme.setCode("DMIC");
        programme.setName("Delhi Mumbai Industrial Corridor");
        programme.setSortOrder(0);
        programme = epsNodeRepository.save(programme);

        Map<String, EpsNode> geoNodes = new HashMap<>();
        String[][] geoSpecs = {
                {"DMIC-N03", "Dholera Special Investment Region (Gujarat)"},
                {"DMIC-N04", "Shendra-Bidkin Industrial Area (Maharashtra)"},
                {"DMIC-N05", "Khushkhera-Bhiwadi-Neemrana (Rajasthan)"},
                {"DMIC-N06", "Pithampur-Dhar-Mhow (Madhya Pradesh)"},
                {"DMIC-N08", "Ponneri (Tamil Nadu)"}
        };
        int order = 0;
        for (String[] spec : geoSpecs) {
            EpsNode geo = new EpsNode();
            geo.setCode(spec[0]);
            geo.setName(spec[1]);
            geo.setParentId(programme.getId());
            geo.setSortOrder(order++);
            geoNodes.put(spec[0], epsNodeRepository.save(geo));
        }

        // --- OBS: one node per organisation, tree via parent_organisation_id ---
        Map<String, ObsNode> obsNodes = new HashMap<>();
        // First pass: insert all OBS rows without parent, keyed by org code
        for (Organisation org : orgs.values()) {
            ObsNode o = new ObsNode();
            o.setCode(org.getCode());
            o.setName(org.getName());
            o.setDescription(org.getOrganisationType().name());
            o.setSortOrder(0);
            obsNodes.put(org.getCode(), obsNodeRepository.save(o));
        }
        // Second pass: wire parentId from organisations.parent_organisation_id
        int obsOrder = 0;
        for (Organisation org : orgs.values()) {
            ObsNode node = obsNodes.get(org.getCode());
            node.setSortOrder(obsOrder++);
            if (org.getParentOrganisationId() != null) {
                orgs.values().stream()
                        .filter(o -> o.getId().equals(org.getParentOrganisationId()))
                        .findFirst()
                        .ifPresent(parent -> node.setParentId(obsNodes.get(parent.getCode()).getId()));
            }
            obsNodeRepository.save(node);
        }

        // --- Master programme project (EPS = DMIC root, OBS = DMICDC) ---
        Project project = new Project();
        project.setCode("DMIC-PROG");
        project.setName("DMIC Master Programme");
        project.setDescription("Delhi Mumbai Industrial Corridor — master programme project");
        project.setEpsNodeId(programme.getId());
        project.setObsNodeId(obsNodes.get("DMICDC").getId());
        project.setPlannedStartDate(LocalDate.of(2023, 4, 1));
        project.setPlannedFinishDate(LocalDate.of(2029, 3, 31));
        project.setDataDate(LocalDate.of(2026, 4, 1));
        project.setStatus(ProjectStatus.ACTIVE);
        project.setPriority(100);
        project.setCategory("EXPRESSWAY");
        project.setMorthCode("DMIC-01");
        project.setFromChainageM(0L);
        project.setToChainageM(1504_000L);
        project.setFromLocation("Delhi");
        project.setToLocation("Mumbai");
        project.setTotalLengthKm(new java.math.BigDecimal("1504.000"));
        project = projectRepository.save(project);

        return new DmicHierarchy(programme, geoNodes, obsNodes, project);
    }

    // ---------------------- WBS ----------------------

    private Map<String, WbsNode> seedWbs(DmicHierarchy h, Map<String, Organisation> orgs) {
        Map<String, WbsNode> map = new HashMap<>();
        UUID pmcId = orgs.get("AECOM-TYPSA").getId();
        UUID dmicdcId = orgs.get("DMICDC").getId();
        UUID lntId = orgs.get("LNT-IDPL").getId();
        UUID tataId = orgs.get("TATA-PROJ").getId();
        UUID afconsId = orgs.get("AFCONS").getId();

        // WBS nodes point at the responsible organisation's OBS node (org tree).
        UUID dmicdcObs = h.obsNodes.get("DMICDC").getId();

        // Level 1: Programme root — owned by DMICDC (SPV)
        WbsNode root = wbs(map, "DMIC", "DMIC Programme", null, h.project.getId(), dmicdcObs,
                0, 1, WbsType.PROGRAMME, WbsPhase.PROGRAMME, WbsStatus.ACTIVE, dmicdcId,
                LocalDate.of(2023, 4, 1), LocalDate.of(2029, 3, 31),
                new BigDecimal("150000.00"), null, AssetClass.ROAD);

        // Level 2: 5 corridor nodes — still owned by DMICDC at programme level
        String[][] level2 = {
                {"DMIC-N03", "Dholera SIR Node", "DMIC", "22000.00", "ROAD"},
                {"DMIC-N04", "Shendra-Bidkin Node", "DMIC", "18500.00", "ROAD"},
                {"DMIC-N05", "Khushkhera-Bhiwadi-Neemrana Node", "DMIC", "17800.00", "ROAD"},
                {"DMIC-N06", "Pithampur-Dhar-Mhow Node", "DMIC", "16900.00", "ROAD"},
                {"DMIC-N08", "Ponneri Node", "DMIC", "12200.00", "ROAD"}
        };
        int order = 1;
        for (String[] n : level2) {
            wbs(map, n[0], n[1], n[2], h.project.getId(), dmicdcObs, order++, 2,
                    WbsType.NODE, WbsPhase.CONSTRUCTION, WbsStatus.IN_PROGRESS, dmicdcId,
                    LocalDate.of(2023, 4, 1), LocalDate.of(2028, 12, 31),
                    new BigDecimal(n[3]), "POLY-" + n[0], AssetClass.valueOf(n[4]));
        }

        // Level 3: packages (5 per node, highest-profile)
        seedNodePackages(map, h, "DMIC-N03", new String[][]{
                {"DMIC-N03-P01", "Township Infrastructure Package 1 (N03)", "2200.00", "ROAD", "POLY-N03-P01"},
                {"DMIC-N03-P02", "Water Supply & Treatment Package (N03)", "1800.00", "WATER", "POLY-N03-P02"},
                {"DMIC-N03-P03", "Power Distribution & Substations (N03)", "2500.00", "POWER", "POLY-N03-P03"},
                {"DMIC-N03-P04", "ICT/Smart City Systems (N03)", "1400.00", "ICT", "POLY-N03-P04"},
                {"DMIC-N03-P05", "Green Infrastructure & Landscaping (N03)", "900.00", "GREEN_INFRASTRUCTURE", "POLY-N03-P05"}
        }, lntId, "LNT-IDPL");
        seedNodePackages(map, h, "DMIC-N04", new String[][]{
                {"DMIC-N04-P01", "Shendra Trunk Roads Package", "1600.00", "ROAD", "POLY-N04-P01"},
                {"DMIC-N04-P02", "Bidkin Industrial Water Supply", "1400.00", "WATER", "POLY-N04-P02"},
                {"DMIC-N04-P03", "Substation & Transmission (N04)", "1900.00", "POWER", "POLY-N04-P03"}
        }, tataId, "TATA-PROJ");
        seedNodePackages(map, h, "DMIC-N05", new String[][]{
                {"DMIC-N05-P01", "Neemrana-Bhiwadi Arterial Roads", "1500.00", "ROAD", "POLY-N05-P01"},
                {"DMIC-N05-P02", "Dedicated Freight Corridor Spur (N05)", "2100.00", "RAIL", "POLY-N05-P02"}
        }, afconsId, "AFCONS");
        seedNodePackages(map, h, "DMIC-N06", new String[][]{
                {"DMIC-N06-P01", "Pithampur Logistic Hub Roads", "1300.00", "ROAD", "POLY-N06-P01"},
                {"DMIC-N06-P02", "Pithampur Power Distribution", "1100.00", "POWER", "POLY-N06-P02"}
        }, tataId, "TATA-PROJ");
        seedNodePackages(map, h, "DMIC-N08", new String[][]{
                {"DMIC-N08-P01", "Ponneri SIR Trunk Roads", "1200.00", "ROAD", "POLY-N08-P01"},
                {"DMIC-N08-P02", "Ponneri ICT & Smart Utility Monitoring", "800.00", "ICT", "POLY-N08-P02"}
        }, lntId, "LNT-IDPL");

        // Level 4: Work packages (subset, sample fidelity for the Satellite Gate scenario)
        seedWorkPackages(map, h, "DMIC-N03-P01", new String[][]{
                {"DMIC-N03-P01-WP01", "Trunk Road Earthworks (CH 0-5 km)", "650.00", "ROAD", "POLY-N03-P01-WP01"},
                {"DMIC-N03-P01-WP02", "Pavement Laying Sections 1-3", "720.00", "ROAD", "POLY-N03-P01-WP02"},
                {"DMIC-N03-P01-WP03", "Box Culverts & Drainage Structures", "430.00", "ROAD", "POLY-N03-P01-WP03"},
                {"DMIC-N03-P01-WP04", "Utility Corridor Ducts", "220.00", "ICT", "POLY-N03-P01-WP04"},
                {"DMIC-N03-P01-WP05", "Road Furniture & Signage", "180.00", "ROAD", "POLY-N03-P01-WP05"}
        }, lntId, "LNT-IDPL");
        // Phase M2 activities reference these WBS work packages for N03-P02/P03/P04
        seedWorkPackages(map, h, "DMIC-N03-P02", new String[][]{
                {"DMIC-N03-P02-WP01", "WTP Intake, Civil & M&E Works", "1100.00", "WATER", "POLY-N03-P02-WP01"},
                {"DMIC-N03-P02-WP02", "Water Transmission Main (DN 1200mm)", "700.00", "WATER", "POLY-N03-P02-WP02"}
        }, lntId, "LNT-IDPL");
        seedWorkPackages(map, h, "DMIC-N03-P03", new String[][]{
                {"DMIC-N03-P03-WP01", "Sub-station Civil Works & GIS Building", "600.00", "POWER", "POLY-N03-P03-WP01"},
                {"DMIC-N03-P03-WP02", "GIS Switchgear, Transformers & 33kV", "1700.00", "POWER", "POLY-N03-P03-WP02"},
                {"DMIC-N03-P03-WP03", "220kV Bay HV Testing & Commissioning", "200.00", "POWER", "POLY-N03-P03-WP03"}
        }, lntId, "LNT-IDPL");
        seedWorkPackages(map, h, "DMIC-N03-P04", new String[][]{
                {"DMIC-N03-P04-WP01", "ICT Backbone Ring Fibre Optic Network", "900.00", "ICT", "POLY-N03-P04-WP01"},
                {"DMIC-N03-P04-WP02", "Smart City Command Centre (SCCC) Building", "500.00", "ICT", "POLY-N03-P04-WP02"}
        }, lntId, "LNT-IDPL");
        seedWorkPackages(map, h, "DMIC-N04-P01", new String[][]{
                {"DMIC-N04-P01-WP01", "Shendra Main Arterial Package A", "900.00", "ROAD", "POLY-N04-P01-WP01"},
                {"DMIC-N04-P01-WP02", "Shendra Main Arterial Package B", "700.00", "ROAD", "POLY-N04-P01-WP02"}
        }, tataId, "TATA-PROJ");
        seedWorkPackages(map, h, "DMIC-N05-P02", new String[][]{
                {"DMIC-N05-P02-WP01", "DFC Track Bed Construction (Neemrana Spur)", "1200.00", "RAIL", "POLY-N05-P02-WP01"},
                {"DMIC-N05-P02-WP02", "DFC Electrification & OHE", "900.00", "POWER", "POLY-N05-P02-WP02"}
        }, afconsId, "AFCONS");

        log.info("[IC-PMS Phase A] seeded {} WBS elements", map.size());
        return map;
    }

    private void seedNodePackages(Map<String, WbsNode> map, DmicHierarchy h, String parentCode,
                                  String[][] specs, UUID contractorOrgId, String contractorCode) {
        UUID obsId = h.obsNodes.get(contractorCode).getId();
        int order = 1;
        for (String[] s : specs) {
            wbs(map, s[0], s[1], parentCode, h.project.getId(),
                    obsId, order++, 3,
                    WbsType.PACKAGE, WbsPhase.CONSTRUCTION, WbsStatus.IN_PROGRESS, contractorOrgId,
                    LocalDate.of(2024, 1, 1), LocalDate.of(2027, 12, 31),
                    new BigDecimal(s[2]), s[4], AssetClass.valueOf(s[3]));
        }
    }

    private void seedWorkPackages(Map<String, WbsNode> map, DmicHierarchy h, String parentCode,
                                  String[][] specs, UUID contractorOrgId, String contractorCode) {
        UUID obsId = h.obsNodes.get(contractorCode).getId();
        int order = 1;
        for (String[] s : specs) {
            wbs(map, s[0], s[1], parentCode, h.project.getId(),
                    obsId, order++, 4,
                    WbsType.WORK_PACKAGE, WbsPhase.CONSTRUCTION, WbsStatus.IN_PROGRESS, contractorOrgId,
                    LocalDate.of(2024, 6, 1), LocalDate.of(2027, 6, 30),
                    new BigDecimal(s[2]), s[4], AssetClass.valueOf(s[3]));
        }
    }

    private WbsNode wbs(Map<String, WbsNode> map, String code, String name, String parentCode,
                        UUID projectId, UUID obsNodeId, int sortOrder, int level,
                        WbsType type, WbsPhase phase, WbsStatus status, UUID responsibleOrgId,
                        LocalDate start, LocalDate finish, BigDecimal budgetCrores,
                        String polygonId, AssetClass assetClass) {
        WbsNode n = new WbsNode();
        n.setCode(code);
        n.setName(name);
        n.setProjectId(projectId);
        n.setObsNodeId(obsNodeId);
        if (parentCode != null && map.containsKey(parentCode)) {
            n.setParentId(map.get(parentCode).getId());
        }
        n.setSortOrder(sortOrder);
        n.setWbsLevel(level);
        n.setWbsType(type);
        n.setPhase(phase);
        n.setWbsStatus(status);
        n.setResponsibleOrganisationId(responsibleOrgId);
        n.setPlannedStart(start);
        n.setPlannedFinish(finish);
        n.setBudgetCrores(budgetCrores);
        n.setGisPolygonId(polygonId);
        n.setAssetClass(assetClass);
        WbsNode saved = wbsNodeRepository.save(n);
        map.put(code, saved);
        return saved;
    }

    // ---------------------- Users ----------------------

    private void seedUsers(Map<String, Organisation> orgs, Map<String, WbsNode> wbs) {
        Role viewerRole = roleRepository.findByName("VIEWER").orElseThrow();
        Role pmRole = roleRepository.findByName("PROJECT_MANAGER").orElseThrow();
        Role schedulerRole = roleRepository.findByName("SCHEDULER").orElseThrow();

        // 20 users per MasterData_OrgUsers sheet
        seedUser("nicdc.secretary", "nicdc.secretary@nicdc.gov.in", "Additional Secretary", "Employer — Director (PMO)",
                orgs.get("NICDC"), viewerRole, EnumSet.of(AuthMethod.NIC_SSO, AuthMethod.DSC_CLASS_3),
                allModulesView(), null); // All corridors
        seedUser("dmicdc.ceo", "ceo@dmicdc.com", "Chief Executive Officer", "SPV — CEO",
                orgs.get("DMICDC"), pmRole, EnumSet.of(AuthMethod.NIC_SSO, AuthMethod.DSC_CLASS_3),
                allModulesEdit(), null);
        seedUser("dmicdc.pd.n03", "pd.n03@dmicdc.com", "Project Director N03", "SPV — Project Director",
                orgs.get("DMICDC"), pmRole, EnumSet.of(AuthMethod.NIC_SSO),
                allModulesEdit(), List.of(wbs.get("DMIC-N03")));
        seedUser("dmicdc.pd.n04", "pd.n04@dmicdc.com", "Project Director N04", "SPV — Project Director",
                orgs.get("DMICDC"), pmRole, EnumSet.of(AuthMethod.NIC_SSO),
                allModulesEdit(), List.of(wbs.get("DMIC-N04")));
        seedUser("diicdc.pd", "pd@diicdc.com", "Project Director Dholera", "SPV — Project Director",
                orgs.get("DIICDC"), pmRole, EnumSet.of(AuthMethod.NIC_SSO),
                allModulesEdit(), List.of(wbs.get("DMIC-N03")));
        seedUser("riico.md", "md@riico.rajasthan.gov.in", "Managing Director", "SPV — MD",
                orgs.get("RIICO"), pmRole, EnumSet.of(AuthMethod.NIC_SSO, AuthMethod.DSC_CLASS_3),
                allModulesEdit(), List.of(wbs.get("DMIC-N05")));
        seedUser("sipcot.md", "md@sipcot.com", "Managing Director", "SPV — MD",
                orgs.get("SIPCOT"), pmRole, EnumSet.of(AuthMethod.NIC_SSO),
                allModulesEdit(), List.of(wbs.get("DMIC-N08")));
        seedUser("aecom.pmc.lead", "pmc.lead@aecom.com", "PMC Team Leader", "PMC — Team Leader",
                orgs.get("AECOM-TYPSA"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                pmcMatrix(), List.of(wbs.get("DMIC-N03"), wbs.get("DMIC-N04")));
        seedUser("aecom.sched", "scheduler@aecom.com", "Lead Scheduler", "PMC — Scheduler",
                orgs.get("AECOM-TYPSA"), schedulerRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                pmcMatrix(), List.of(wbs.get("DMIC-N03")));
        seedUser("egis.pmc.lead", "pmc.lead@egis.com", "PMC Team Leader", "PMC — Team Leader",
                orgs.get("EGIS-PMC"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                pmcMatrix(), List.of(wbs.get("DMIC-N05")));
        seedUser("mott.pmc.lead", "pmc.lead@mottmac.com", "PMC Team Leader", "PMC — Team Leader",
                orgs.get("MOTT-MAC"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                pmcMatrix(), List.of(wbs.get("DMIC-N06"), wbs.get("DMIC-N08")));
        seedUser("lnt.pm.n03", "pm.n03@lntidpl.com", "EPC Project Manager N03-P01", "EPC — Project Manager",
                orgs.get("LNT-IDPL"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                epcMatrix(), List.of(wbs.get("DMIC-N03-P01")));
        seedUser("lnt.sitein", "sitein.n03@lntidpl.com", "EPC Site Engineer", "EPC — Site Engineer",
                orgs.get("LNT-IDPL"), viewerRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                epcMatrix(), List.of(wbs.get("DMIC-N03-P01")));
        seedUser("tata.pm.n04", "pm.n04@tataprojects.com", "EPC Project Manager N04", "EPC — Project Manager",
                orgs.get("TATA-PROJ"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                epcMatrix(), List.of(wbs.get("DMIC-N04-P01")));
        seedUser("afcons.pm.n05", "pm.n05@afcons.com", "EPC Project Manager N05 DFC", "EPC — Project Manager",
                orgs.get("AFCONS"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                epcMatrix(), List.of(wbs.get("DMIC-N05-P02")));
        seedUser("hcc.pm", "pm@hccindia.com", "EPC Project Manager", "EPC — Project Manager",
                orgs.get("HCC"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                epcMatrix(), List.of(wbs.get("DMIC-N06")));
        seedUser("dilip.pm", "pm@dilipbuildcon.com", "EPC Project Manager", "EPC — Project Manager",
                orgs.get("DILIP-BUILDCON"), pmRole, EnumSet.of(AuthMethod.USERNAME_PASSWORD),
                epcMatrix(), List.of(wbs.get("DMIC-N06")));
        seedUser("cag.auditor", "auditor@cag.gov.in", "Principal Director of Audit", "Auditor — CAG",
                orgs.get("CAG"), viewerRole, EnumSet.of(AuthMethod.NIC_SSO, AuthMethod.DSC_CLASS_3),
                auditorMatrix(), null);
        seedUser("cvc.officer", "officer@cvc.nic.in", "Chief Vigilance Officer", "Auditor — CVC",
                orgs.get("CVC"), viewerRole, EnumSet.of(AuthMethod.NIC_SSO, AuthMethod.DSC_CLASS_3),
                auditorMatrix(), null);
        seedUser("aadhaar.citizen", "citizen.portal@mygov.in", "Citizen Portal Viewer", "Public — Citizen",
                orgs.get("NICDC"), viewerRole, EnumSet.of(AuthMethod.AADHAAR_OTP),
                citizenMatrix(), null);

        log.info("[IC-PMS Phase A] seeded 20 IC-PMS users with module access + corridor scope");
    }

    private void seedUser(String username, String email, String designation, String role,
                          Organisation org, Role springRole, Set<AuthMethod> authMethods,
                          Map<IcpmsModule, ModuleAccessLevel> moduleAccess, List<WbsNode> corridors) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User u = new User(username, email, passwordEncoder.encode("ChangeMe@2026"));
        u.setFirstName(username.split("\\.")[0]);
        u.setLastName("User");
        u.setOrganisationId(org.getId());
        u.setDesignation(designation);
        u.setPrimaryIcpmsRole(role);
        u.setAuthMethods(authMethods);
        u.setEnabled(true);
        u = userRepository.save(u);
        // Persist the UserRole join row directly — the @OneToMany on User has no cascade,
        // so simply adding to u.getRoles() doesn't insert the row (which breaks JWT role claims).
        userRoleRepository.save(new UserRole(u.getId(), springRole.getId()));

        for (var entry : moduleAccess.entrySet()) {
            userModuleAccessRepository.save(UserModuleAccess.builder()
                    .userId(u.getId())
                    .module(entry.getKey())
                    .accessLevel(entry.getValue())
                    .build());
        }
        if (corridors == null) {
            userCorridorScopeRepository.save(UserCorridorScope.builder()
                    .userId(u.getId())
                    .wbsNodeId(null) // All Corridors sentinel
                    .build());
        } else {
            for (WbsNode w : corridors) {
                userCorridorScopeRepository.save(UserCorridorScope.builder()
                        .userId(u.getId())
                        .wbsNodeId(w.getId())
                        .build());
            }
        }
    }

    private Map<IcpmsModule, ModuleAccessLevel> allModulesView() {
        Map<IcpmsModule, ModuleAccessLevel> m = new HashMap<>();
        for (IcpmsModule mod : IcpmsModule.values()) {
            m.put(mod, ModuleAccessLevel.VIEW);
        }
        return m;
    }

    private Map<IcpmsModule, ModuleAccessLevel> allModulesEdit() {
        Map<IcpmsModule, ModuleAccessLevel> m = new HashMap<>();
        for (IcpmsModule mod : IcpmsModule.values()) {
            m.put(mod, ModuleAccessLevel.EDIT);
        }
        m.put(IcpmsModule.M4_COST_RA_BILLS, ModuleAccessLevel.APPROVE);
        m.put(IcpmsModule.M5_CONTRACTS, ModuleAccessLevel.APPROVE);
        return m;
    }

    private Map<IcpmsModule, ModuleAccessLevel> pmcMatrix() {
        Map<IcpmsModule, ModuleAccessLevel> m = new HashMap<>();
        m.put(IcpmsModule.M1_WBS_GIS, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M2_SCHEDULE_EVM, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M3_SATELLITE, ModuleAccessLevel.VIEW);
        m.put(IcpmsModule.M4_COST_RA_BILLS, ModuleAccessLevel.CERTIFY);
        m.put(IcpmsModule.M5_CONTRACTS, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M6_DOCUMENTS, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M7_RISKS, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M8_RESOURCES, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M9_REPORTS, ModuleAccessLevel.VIEW);
        return m;
    }

    private Map<IcpmsModule, ModuleAccessLevel> epcMatrix() {
        Map<IcpmsModule, ModuleAccessLevel> m = new HashMap<>();
        m.put(IcpmsModule.M1_WBS_GIS, ModuleAccessLevel.VIEW);
        m.put(IcpmsModule.M2_SCHEDULE_EVM, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M3_SATELLITE, ModuleAccessLevel.VIEW);
        m.put(IcpmsModule.M4_COST_RA_BILLS, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M5_CONTRACTS, ModuleAccessLevel.VIEW);
        m.put(IcpmsModule.M6_DOCUMENTS, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M7_RISKS, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M8_RESOURCES, ModuleAccessLevel.EDIT);
        m.put(IcpmsModule.M9_REPORTS, ModuleAccessLevel.VIEW);
        return m;
    }

    private Map<IcpmsModule, ModuleAccessLevel> auditorMatrix() {
        Map<IcpmsModule, ModuleAccessLevel> m = new HashMap<>();
        for (IcpmsModule mod : IcpmsModule.values()) {
            m.put(mod, ModuleAccessLevel.VIEW);
        }
        return m;
    }

    private Map<IcpmsModule, ModuleAccessLevel> citizenMatrix() {
        Map<IcpmsModule, ModuleAccessLevel> m = new HashMap<>();
        m.put(IcpmsModule.M1_WBS_GIS, ModuleAccessLevel.VIEW);
        m.put(IcpmsModule.M9_REPORTS, ModuleAccessLevel.VIEW);
        return m;
    }
}
