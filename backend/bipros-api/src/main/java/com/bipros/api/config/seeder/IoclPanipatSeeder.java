package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.model.DurationType;
import com.bipros.activity.domain.model.PercentCompleteType;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.application.dto.CreateBaselineRequest;
import com.bipros.baseline.application.service.BaselineService;
import com.bipros.baseline.domain.BaselineType;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarException;
import com.bipros.calendar.domain.model.CalendarType;
import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;
import com.bipros.calendar.domain.repository.CalendarExceptionRepository;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.calendar.domain.repository.CalendarWorkWeekRepository;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.ContractType;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.project.domain.model.EpsNode;
import com.bipros.project.domain.model.ObsNode;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.model.WbsStatus;
import com.bipros.project.domain.model.WbsType;
import com.bipros.project.domain.repository.EpsNodeRepository;
import com.bipros.project.domain.repository.ObsNodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.risk.application.dto.CreateRiskRequest;
import com.bipros.risk.application.service.RiskService;
import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IOCL Panipat — WO 70143247 seeder.
 *
 * <p>Creates a second demo project alongside the DMIC programme: Indian Oil Corporation's
 * "Civil and Mechanical Package for Revamping of Bitumen Filling Plant and augmentation of
 * facilities for bulk TT loading at Panipat Bitumen Plant". Source of truth is
 * {@code docs/iocl-panipat-wo.md} which extracts the 123-page SAP YMPR_SPO PDF.
 *
 * <p>What gets seeded (mirrors scripts/seed-iocl-panipat-wo.sh):
 * <ul>
 *   <li>EPS: IOCL → Panipat Terminal → Bitumen Plant</li>
 *   <li>OBS: DSO → EIC (Angom Rajen Singh) → Site Engineering (Indresh Kumar)</li>
 *   <li>IOCL-6day calendar with 8 India statutory holidays (2024-25)</li>
 *   <li>Project WO70143247 + 29-node WBS (5 L1 work-order groups + 24 L2 sub-groups)</li>
 *   <li>26 activities incl. 2 milestones with planned dates, original duration</li>
 *   <li>Activity network (dismantling → civil/procurement → mechanical → steam-tracing)</li>
 *   <li>Vendor contract DE'S TECHNICO LIMITED, INR 18.93 Cr ex-GST</li>
 *   <li>10 risks wired to real affected activities so the Risk Drivers tab populates</li>
 *   <li>Active PRIMARY baseline snapshot — required for Monte Carlo simulation</li>
 * </ul>
 *
 * <p>Sentinel: project with code {@code WO70143247} — if present, the run is skipped idempotently.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(130)
@RequiredArgsConstructor
public class IoclPanipatSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "WO70143247";
    private static final LocalDate START = LocalDate.of(2024, 8, 1);
    private static final LocalDate FINISH = LocalDate.of(2025, 6, 30);
    private static final LocalDate WO_DATE = LocalDate.of(2024, 7, 19);
    private static final BigDecimal CONTRACT_VALUE = new BigDecimal("189370825.01");

    private final EpsNodeRepository epsNodeRepository;
    private final ObsNodeRepository obsNodeRepository;
    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final CalendarRepository calendarRepository;
    private final CalendarWorkWeekRepository calendarWorkWeekRepository;
    private final CalendarExceptionRepository calendarExceptionRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRelationshipRepository relationshipRepository;
    private final ContractRepository contractRepository;
    private final RiskService riskService;
    private final BaselineService baselineService;

    @Override
    @Transactional
    public void run(String... args) {
        if (projectRepository.findByCode(PROJECT_CODE).isPresent()) {
            log.info("[IOCL Panipat] project '{}' already seeded, skipping", PROJECT_CODE);
            return;
        }
        log.info("[IOCL Panipat] seeding WO 70143247 …");

        UUID epsLeaf = seedEps();
        UUID obsLeaf = seedObs();
        UUID calendarId = seedCalendar();
        Project project = seedProject(epsLeaf, obsLeaf);
        Map<String, UUID> wbs = seedWbs(project.getId());
        Map<String, UUID> activities = seedActivities(project.getId(), calendarId, wbs);
        seedRelationships(activities);
        seedContract(project.getId());
        seedRisks(project.getId(), activities);
        // Capture an active PRIMARY baseline — required for Monte Carlo simulation runs.
        baselineService.createBaseline(project.getId(), new CreateBaselineRequest(
            "WO-approved baseline",
            BaselineType.PRIMARY,
            "Baseline captured immediately after WO issue (19-Jul-2024) and before site mobilisation."));

        log.info("[IOCL Panipat] done: {} WBS nodes, {} activities, 10 risks, active baseline",
            wbs.size(), activities.size());
    }

    // ─────────────────────────── EPS ───────────────────────────
    private UUID seedEps() {
        EpsNode iocl = saveEps("IOCL", "Indian Oil Corporation Ltd", null, 0);
        EpsNode panipat = saveEps("PANIPAT", "Panipat Terminal", iocl.getId(), 0);
        EpsNode bitumen = saveEps("PNP-BITUMEN", "Bitumen Plant", panipat.getId(), 0);
        return bitumen.getId();
    }

    private EpsNode saveEps(String code, String name, UUID parentId, int order) {
        EpsNode n = new EpsNode();
        n.setCode(code);
        n.setName(name);
        n.setParentId(parentId);
        n.setSortOrder(order);
        return epsNodeRepository.save(n);
    }

    // ─────────────────────────── OBS ───────────────────────────
    private UUID seedObs() {
        ObsNode dso = saveObs("IOCL-DSO", "IOCL Direct Supply Office — Engineering", null, 0);
        ObsNode eic = saveObs("IOCL-EIC", "Angom Rajen Singh — EIC, DGM (Engg.)", dso.getId(), 0);
        ObsNode site = saveObs("IOCL-SITE-ENG", "Indresh Kumar — Site Engineer, Asst. Mgr", eic.getId(), 0);
        return site.getId();
    }

    private ObsNode saveObs(String code, String name, UUID parentId, int order) {
        ObsNode o = new ObsNode();
        o.setCode(code);
        o.setName(name);
        o.setParentId(parentId);
        o.setSortOrder(order);
        return obsNodeRepository.save(o);
    }

    // ────────────────────────── Calendar ───────────────────────
    private UUID seedCalendar() {
        Calendar cal = Calendar.builder()
            .code("IOCL-6day")
            .name("IOCL Panipat 6-day refinery calendar")
            .description("IOCL refinery convention: Mon-Sat WORKING (8 hrs/day), Sunday NON_WORKING, "
                + "+ 8 India statutory holidays 2024-25 (Independence Day, Gandhi Jayanti, Diwali, "
                + "Holi, Christmas, Republic Day, Good Friday, Dussehra).")
            .calendarType(CalendarType.GLOBAL)
            .isDefault(false)
            .standardWorkHoursPerDay(8.0)
            .standardWorkDaysPerWeek(6)
            .build();
        Calendar saved = calendarRepository.save(cal);

        for (DayOfWeek day : DayOfWeek.values()) {
            boolean working = day != DayOfWeek.SUNDAY;
            CalendarWorkWeek ww = CalendarWorkWeek.builder()
                .calendarId(saved.getId())
                .dayOfWeek(day)
                .dayType(working ? DayType.WORKING : DayType.NON_WORKING)
                .totalWorkHours(working ? 8.0 : 0.0)
                .startTime1(working ? LocalTime.of(8, 0) : null)
                .endTime1(working ? LocalTime.of(12, 0) : null)
                .startTime2(working ? LocalTime.of(13, 0) : null)
                .endTime2(working ? LocalTime.of(17, 0) : null)
                .build();
            calendarWorkWeekRepository.save(ww);
        }

        // India statutory holidays (2024-25 window covering the WO execution period).
        String[][] holidays = {
            {"2024-08-15", "Independence Day"},
            {"2024-10-02", "Gandhi Jayanti"},
            {"2024-10-12", "Dussehra"},
            {"2024-11-01", "Diwali"},
            {"2024-12-25", "Christmas"},
            {"2025-01-26", "Republic Day"},
            {"2025-03-14", "Holi"},
            {"2025-04-18", "Good Friday"}
        };
        for (String[] h : holidays) {
            calendarExceptionRepository.save(CalendarException.builder()
                .calendarId(saved.getId())
                .exceptionDate(LocalDate.parse(h[0]))
                .dayType(DayType.EXCEPTION_NON_WORKING)
                .name(h[1])
                .build());
        }
        return saved.getId();
    }

    // ────────────────────────── Project ────────────────────────
    private Project seedProject(UUID epsLeafId, UUID obsLeafId) {
        Project p = new Project();
        p.setCode(PROJECT_CODE);
        p.setName("IOCL Panipat — Bitumen Filling Plant Revamp (WO 70143247)");
        p.setDescription("Civil and Mechanical Package for Revamping of Bitumen Filling Plant and "
            + "augmentation of facilities for bulk TT loading at Panipat Bitumen Plant. "
            + "SAP PO PT-09/70143247 dated 2024-07-19. Vendor: DE'S TECHNICO LIMITED "
            + "(code 10108488). Completion period 11 months.");
        p.setEpsNodeId(epsLeafId);
        p.setObsNodeId(obsLeafId);
        p.setPlannedStartDate(START);
        p.setPlannedFinishDate(FINISH);
        p.setDataDate(START);
        p.setStatus(ProjectStatus.ACTIVE);
        p.setPriority(90);
        return projectRepository.save(p);
    }

    // ──────────────────────────── WBS ──────────────────────────
    private Map<String, UUID> seedWbs(UUID projectId) {
        Map<String, UUID> map = new HashMap<>();

        // Level 1 — the 5 SAP work-order groupings
        UUID civL1 = saveWbs(map, "WO70143247-CIV", "00010 — Civil Works (TT bulk loading)",
            null, projectId, 1, 1);
        UUID mechL1 = saveWbs(map, "WO70143247-MECH", "00020 — Part-B Mechanical work",
            null, projectId, 2, 1);
        UUID valL1 = saveWbs(map, "WO70143247-VAL", "00030 — Part-C Valves / CS pipe supply",
            null, projectId, 3, 1);
        UUID stmL1 = saveWbs(map, "WO70143247-STM", "00040 — Part-D Steam/condensate tracing",
            null, projectId, 4, 1);
        UUID dmtL1 = saveWbs(map, "WO70143247-DMT", "00050 — Part-E Dismantling",
            null, projectId, 5, 1);

        // Level 2 — major sub-groups per L1 (24 total; matches docs/iocl-panipat-wo.md §3)
        int o = 1;
        saveWbs(map, "CIV-CLEAR", "Site clearing", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-EARTH", "Earthwork / excavation / filling", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-CONC", "Concrete (PCC/RCC)", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-MASN", "Masonry", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-PLAS", "Plaster & flooring", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-WPROOF", "Waterproofing & roofing", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-DOORS", "Doors & windows", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-PLMB", "Sanitary & plumbing", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-PAINT", "Painting", civL1, projectId, o++, 2);
        saveWbs(map, "CIV-MISC", "Civil miscellaneous (fencing/drains)", civL1, projectId, o++, 2);

        o = 1;
        saveWbs(map, "VAL-BALL", "Valves (ball/globe/check) supply & install", valL1, projectId, o++, 2);
        saveWbs(map, "VAL-CSPIPE", "CS pipe supply (100-200 NB)", valL1, projectId, o++, 2);
        saveWbs(map, "VAL-SEAM", "Seamless pipe supply & install", valL1, projectId, o++, 2);
        saveWbs(map, "VAL-FLNG", "Flange & gasket install", valL1, projectId, o++, 2);

        o = 1;
        saveWbs(map, "MEC-LAY", "Bitumen pipe laying & welding", mechL1, projectId, o++, 2);
        saveWbs(map, "MEC-SUP", "Pipe supports installation", mechL1, projectId, o++, 2);
        saveWbs(map, "MEC-HYDRO", "Hydrotesting (1.5× DP, 4 hrs)", mechL1, projectId, o++, 2);
        saveWbs(map, "MEC-DPT", "DP testing per QAP", mechL1, projectId, o++, 2);

        o = 1;
        saveWbs(map, "STM-TRACE", "Steam heat tracing (≤50 NB)", stmL1, projectId, o++, 2);
        saveWbs(map, "STM-SMAN", "Steam manifold install (4/8/12 way)", stmL1, projectId, o++, 2);
        saveWbs(map, "STM-CMAN", "Condensate collection manifold", stmL1, projectId, o++, 2);
        saveWbs(map, "STM-TRAP", "Strainers / traps / piston valves", stmL1, projectId, o++, 2);

        o = 1;
        saveWbs(map, "DMT-PIPE", "Dismantle existing piping (45,000 m)", dmtL1, projectId, o++, 2);
        saveWbs(map, "DMT-PUMP", "Dismantle existing bitumen pumps", dmtL1, projectId, o++, 2);

        return map;
    }

    private UUID saveWbs(Map<String, UUID> map, String code, String name, UUID parentId,
                         UUID projectId, int sortOrder, int level) {
        WbsNode n = new WbsNode();
        n.setCode(code);
        n.setName(name);
        n.setProjectId(projectId);
        n.setParentId(parentId);
        n.setSortOrder(sortOrder);
        n.setWbsLevel(level);
        n.setWbsType(level == 1 ? WbsType.PACKAGE : WbsType.WORK_PACKAGE);
        n.setWbsStatus(WbsStatus.NOT_STARTED);
        n.setPlannedStart(START);
        n.setPlannedFinish(FINISH);
        UUID id = wbsNodeRepository.save(n).getId();
        map.put(code, id);
        return id;
    }

    // ───────────────────────── Activities ──────────────────────
    private Map<String, UUID> seedActivities(UUID projectId, UUID calendarId, Map<String, UUID> wbs) {
        Map<String, UUID> map = new HashMap<>();

        // Milestones (duration 0)
        act(map, "MS-START", "Project Start — LOA received", wbs.get("CIV-CLEAR"), projectId, calendarId,
            ActivityType.START_MILESTONE, 0, START, START);
        act(map, "MS-END", "Project Completion (11 months)", wbs.get("CIV-MISC"), projectId, calendarId,
            ActivityType.FINISH_MILESTONE, 0, FINISH, FINISH);

        // Dismantling (~3 weeks)
        act(map, "A50-01", "Dismantle existing piping (45,000 m)", wbs.get("DMT-PIPE"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 15, date("2024-08-01"), date("2024-08-19"));
        act(map, "A50-02", "Dismantle existing bitumen pumps", wbs.get("DMT-PUMP"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 5, date("2024-08-05"), date("2024-08-10"));

        // Civil (~14 weeks)
        act(map, "A10-01", "Site clearing & grass removal", wbs.get("CIV-CLEAR"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 5, date("2024-08-20"), date("2024-08-26"));
        act(map, "A10-02", "Earthwork / excavation / filling", wbs.get("CIV-EARTH"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 20, date("2024-08-27"), date("2024-09-21"));
        act(map, "A10-03", "PCC / RCC / concrete works", wbs.get("CIV-CONC"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 25, date("2024-09-23"), date("2024-10-24"));
        act(map, "A10-04", "Masonry", wbs.get("CIV-MASN"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 15, date("2024-10-25"), date("2024-11-13"));
        act(map, "A10-05", "Plaster & flooring", wbs.get("CIV-PLAS"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 10, date("2024-11-14"), date("2024-11-26"));
        act(map, "A10-06", "Waterproofing & roofing", wbs.get("CIV-WPROOF"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 10, date("2024-11-14"), date("2024-11-26"));
        act(map, "A10-07", "Doors & windows", wbs.get("CIV-DOORS"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 7, date("2024-11-27"), date("2024-12-05"));
        act(map, "A10-08", "Sanitary & plumbing", wbs.get("CIV-PLMB"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 10, date("2024-11-27"), date("2024-12-09"));
        act(map, "A10-09", "Painting", wbs.get("CIV-PAINT"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 8, date("2024-12-10"), date("2024-12-19"));
        act(map, "A10-10", "Civil miscellaneous (fencing/drains)", wbs.get("CIV-MISC"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 15, date("2024-12-20"), date("2025-01-10"));

        // Valves & supply (~10 weeks)
        act(map, "A30-01", "Valve procurement & install (ball/globe/check)", wbs.get("VAL-BALL"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 30, date("2024-09-01"), date("2024-10-07"));
        act(map, "A30-02", "CS pipe supply (100-200 NB)", wbs.get("VAL-CSPIPE"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 25, date("2024-09-01"), date("2024-09-30"));
        act(map, "A30-03", "Seamless pipe supply & install", wbs.get("VAL-SEAM"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 20, date("2024-09-15"), date("2024-10-08"));
        act(map, "A30-04", "Flange & gasket install", wbs.get("VAL-FLNG"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 15, date("2024-10-01"), date("2024-10-18"));

        // Mechanical pipeline (~9 weeks)
        act(map, "A20-01", "Bitumen pipe laying & welding", wbs.get("MEC-LAY"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 40, date("2024-10-09"), date("2024-11-25"));
        act(map, "A20-02", "Pipe supports installation", wbs.get("MEC-SUP"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 20, date("2024-10-15"), date("2024-11-07"));
        act(map, "A20-03", "Hydrotesting (1.5× DP, 4 hrs)", wbs.get("MEC-HYDRO"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 8, date("2024-11-26"), date("2024-12-04"));
        act(map, "A20-04", "DP testing per QAP", wbs.get("MEC-DPT"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 5, date("2024-12-05"), date("2024-12-10"));

        // Steam tracing (~12 weeks)
        act(map, "A40-01", "Steam heat tracing (≤50 NB)", wbs.get("STM-TRACE"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 25, date("2024-12-11"), date("2025-01-11"));
        act(map, "A40-02", "Steam manifold install (4/8/12 way)", wbs.get("STM-SMAN"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 15, date("2025-01-13"), date("2025-01-30"));
        act(map, "A40-03", "Condensate collection manifold", wbs.get("STM-CMAN"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 15, date("2025-02-01"), date("2025-02-19"));
        act(map, "A40-04", "Strainers / traps / piston valves", wbs.get("STM-TRAP"), projectId, calendarId,
            ActivityType.TASK_DEPENDENT, 10, date("2025-02-20"), date("2025-03-04"));

        return map;
    }

    private void act(Map<String, UUID> map, String code, String name, UUID wbsNodeId,
                     UUID projectId, UUID calendarId, ActivityType type,
                     double duration, LocalDate plannedStart, LocalDate plannedFinish) {
        // MS-END lives under the last CIV sub-group, which we create below after the milestone
        // was attempted. Fallback to any valid WBS node if the expected one isn't mapped yet.
        if (wbsNodeId == null) {
            throw new IllegalStateException("WBS node for activity " + code + " is not yet seeded");
        }
        Activity a = new Activity();
        a.setCode(code);
        a.setName(name);
        a.setProjectId(projectId);
        a.setWbsNodeId(wbsNodeId);
        a.setCalendarId(calendarId);
        a.setActivityType(type);
        a.setDurationType(DurationType.FIXED_DURATION_AND_UNITS);
        a.setPercentCompleteType(PercentCompleteType.DURATION);
        a.setStatus(ActivityStatus.NOT_STARTED);
        a.setOriginalDuration(duration);
        a.setRemainingDuration(duration);
        a.setAtCompletionDuration(duration);
        a.setPlannedStartDate(plannedStart);
        a.setPlannedFinishDate(plannedFinish);
        a.setPercentComplete(0.0);
        a.setIsCritical(false);
        map.put(code, activityRepository.save(a).getId());
    }

    // ────────────────────────── Relationships ──────────────────
    private void seedRelationships(Map<String, UUID> a) {
        // Start → Dismantling (both streams)
        rel(a.get("MS-START"), a.get("A50-01"));
        rel(a.get("MS-START"), a.get("A50-02"));

        // Dismantling → Civil start
        rel(a.get("A50-01"), a.get("A10-01"));
        rel(a.get("A50-02"), a.get("A10-01"));

        // Parallel: procurement kicks off from Start with 10-day lag
        relLag(a.get("MS-START"), a.get("A30-01"), 10);
        relLag(a.get("MS-START"), a.get("A30-02"), 10);

        // Civil chain
        rel(a.get("A10-01"), a.get("A10-02"));
        rel(a.get("A10-02"), a.get("A10-03"));
        rel(a.get("A10-03"), a.get("A10-04"));
        rel(a.get("A10-04"), a.get("A10-05"));
        rel(a.get("A10-04"), a.get("A10-06"));
        rel(a.get("A10-05"), a.get("A10-07"));
        rel(a.get("A10-05"), a.get("A10-08"));
        rel(a.get("A10-07"), a.get("A10-09"));
        rel(a.get("A10-09"), a.get("A10-10"));

        // Valves chain
        rel(a.get("A30-02"), a.get("A30-03"));
        rel(a.get("A30-01"), a.get("A30-04"));

        // Mechanical — depends on valves + civil concrete
        rel(a.get("A30-03"), a.get("A20-01"));
        rel(a.get("A30-04"), a.get("A20-01"));
        rel(a.get("A10-03"), a.get("A20-01"));
        rel(a.get("A20-01"), a.get("A20-02"));
        rel(a.get("A20-02"), a.get("A20-03"));
        rel(a.get("A20-03"), a.get("A20-04"));

        // Steam tracing chain — after mechanical
        rel(a.get("A20-04"), a.get("A40-01"));
        rel(a.get("A40-01"), a.get("A40-02"));
        rel(a.get("A40-02"), a.get("A40-03"));
        rel(a.get("A40-03"), a.get("A40-04"));

        // End milestone
        rel(a.get("A40-04"), a.get("MS-END"));
        rel(a.get("A10-10"), a.get("MS-END"));
    }

    private void rel(UUID pred, UUID succ) { relLag(pred, succ, 0); }

    private void relLag(UUID pred, UUID succ, double lag) {
        if (pred == null || succ == null) return;
        ActivityRelationship r = new ActivityRelationship();
        r.setPredecessorActivityId(pred);
        r.setSuccessorActivityId(succ);
        r.setRelationshipType(RelationshipType.FINISH_TO_START);
        r.setLag(lag);
        // projectId comes from either endpoint's activity.
        Activity p = activityRepository.findById(pred).orElseThrow();
        r.setProjectId(p.getProjectId());
        r.setIsExternal(false);
        relationshipRepository.save(r);
    }

    // ─────────────────────────── Contract ──────────────────────
    private void seedContract(UUID projectId) {
        Contract c = new Contract();
        c.setProjectId(projectId);
        c.setContractNumber("70143247");
        c.setLoaNumber("PT-09/70143247");
        c.setContractorName("DE'S TECHNICO LIMITED");
        c.setContractorCode("10108488");
        c.setContractValue(CONTRACT_VALUE);
        c.setLoaDate(WO_DATE);
        c.setStartDate(START);
        c.setCompletionDate(FINISH);
        c.setContractType(ContractType.ITEM_RATE_FIDIC_RED);
        c.setStatus(ContractStatus.ACTIVE);
        contractRepository.save(c);
    }

    // ─────────────────────────── Risks ─────────────────────────
    private void seedRisks(UUID projectId, Map<String, UUID> act) {
        risk(projectId, "R01", "Phased handover from IOCL ops",
            "Facilities cannot be handed over in one go; progressive handover needed per IOCL ops/maintenance/safety sign-off.",
            "ORGANIZATIONAL", RiskProbability.HIGH, RiskImpact.HIGH,
            30, null, csv(act, "A50-01", "A50-02", "A10-01"));
        risk(projectId, "R02", "Dismantling wastage > 20% penalty",
            "Wastage > 20% on dismantled piping material is penalised per contract.",
            "COST", RiskProbability.MEDIUM, RiskImpact.MEDIUM,
            null, new BigDecimal("500000"), csv(act, "A50-01"));
        risk(projectId, "R03", "Insulation disposal compliance",
            "Old pipe insulation must go to recycling agency with certificate submitted to IOCL.",
            "STATUTORY_CLEARANCE", RiskProbability.MEDIUM, RiskImpact.LOW,
            3, new BigDecimal("150000"), csv(act, "A50-01"));
        risk(projectId, "R04", "Free-issue CS pipe handling",
            "IOCL supplies CS pipes; vendor handles site-wide transport at no extra cost.",
            "RESOURCE", RiskProbability.LOW, RiskImpact.LOW,
            2, null, csv(act, "A30-02", "A30-03"));
        risk(projectId, "R05", "Hydrotest failure rework",
            "Hydrotest at 1.5×DP for 4 hrs + DP on all joints. Any joint failure redoes entire section.",
            "QUALITY", RiskProbability.MEDIUM, RiskImpact.HIGH,
            14, new BigDecimal("1500000"), csv(act, "A20-03", "A20-04", "A20-01"));
        risk(projectId, "R06", "IOCL clearance bottleneck",
            "Every dismantling step needs clearance from IOCL ops/maint/safety (3 sign-offs).",
            "PROJECT_MANAGEMENT", RiskProbability.HIGH, RiskImpact.MEDIUM,
            10, null, csv(act, "A50-01", "A50-02"));
        risk(projectId, "R07", "Material ID damage on re-use",
            "Material ID marks must be retained on dismantled pipes/fittings; damaged marks mean re-procurement.",
            "QUALITY", RiskProbability.LOW, RiskImpact.MEDIUM,
            5, new BigDecimal("800000"), csv(act, "A50-01", "A20-01"));
        risk(projectId, "R08", "Scope exclusions — commissioning/training",
            "PDF does not list commissioning support, testing, or operator training. Confirm with EIC.",
            "TECHNICAL", RiskProbability.MEDIUM, RiskImpact.MEDIUM,
            7, new BigDecimal("600000"), csv(act, "A40-04"));
        risk(projectId, "R09", "Monsoon impact on civil earthworks",
            "August start enters North-India monsoon season (Jul-Sep); civil earthwork productivity hit.",
            "MONSOON_IMPACT", RiskProbability.HIGH, RiskImpact.MEDIUM,
            14, null, csv(act, "A10-02", "A10-01", "A10-03"));
        risk(projectId, "R10", "Live refinery concurrent ops",
            "ISO-accredited running plant; all welding under hot-work permit, safety constraints.",
            "EXTERNAL", RiskProbability.HIGH, RiskImpact.HIGH,
            7, new BigDecimal("400000"), csv(act, "A20-01", "A20-03"));
    }

    private void risk(UUID projectId, String code, String title, String description,
                      String legacyCategoryName, RiskProbability probability, RiskImpact impact,
                      Integer scheduleImpactDays, BigDecimal costImpact, String affected) {
        riskService.createRisk(projectId, CreateRiskRequest.builder()
            .code(code)
            .title(title)
            .description(description)
            .legacyCategoryCode(LegacyRiskCategoryLookup.codeForLegacyEnum(legacyCategoryName))
            .probability(probability)
            .impact(impact)
            .identifiedDate(WO_DATE)
            .scheduleImpactDays(scheduleImpactDays)
            .costImpact(costImpact)
            .affectedActivities(affected)
            .build());
    }

    /** Comma-joined UUID string for the given activity keys — matches the format
     *  MonteCarloEngine's risk-driver parser accepts. */
    private String csv(Map<String, UUID> act, String... keys) {
        List<String> ids = new ArrayList<>(keys.length);
        for (String k : keys) {
            UUID id = act.get(k);
            if (id != null) ids.add(id.toString());
        }
        return String.join(",", ids);
    }

    private static LocalDate date(String iso) { return LocalDate.parse(iso); }
}
