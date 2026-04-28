package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.model.DurationType;
import com.bipros.activity.domain.model.PercentCompleteType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IC-PMS M2 — schedule activities for the Dholera SIR (DMIC-N03) corridor.
 *
 * <p>Seeds the 34 activities (30 tasks + 4 milestones) published in the canonical
 * {@code M2_Schedule_Activities} sample sheet. Every activity carries planned and
 * actual dates, original/remaining duration, physical %, total float and the
 * critical-path flag so the M2 schedule view and M9 reports have realistic data
 * to demonstrate the scheduling engine.
 *
 * <p>BCWS/BCWP/ACWP values from the Excel are retained in each activity's
 * {@code notes} field for now — a dedicated EVM activity-level snapshot table
 * can read them later if we promote EVM from the monthly programme level down
 * to the activity level.
 *
 * <p>Runs after all current Phase A–E seeders (Order 101–105) because those wire
 * up WBS, Projects and Calendars that this seeder references via FK.
 */
@Slf4j
@Component
@Profile("dev")
@Order(106)
@RequiredArgsConstructor
public class IcpmsActivitiesSeeder implements CommandLineRunner {

    private final ActivityRepository activityRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final ProjectRepository projectRepository;
    private final CalendarRepository calendarRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (activityRepository.count() > 0) {
            log.info("[IC-PMS M2] activities already present, skipping");
            return;
        }

        // FK lookups
        Project project = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (project == null) {
            log.warn("[IC-PMS M2 GAP] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = project.getId();

        Calendar cal6day = calendarRepository.findAll().stream()
                .filter(c -> "DMIC-6day".equals(c.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DMIC-6day calendar not seeded"));
        UUID calendarId = cal6day.getId();

        Map<String, UUID> wbs = new HashMap<>();
        for (WbsNode n : wbsNodeRepository.findAll()) {
            wbs.put(n.getCode(), n.getId());
        }

        int order = 1;
        // Package 1 — Trunk Road (WP01 earthworks; WP02 sub-base; WP03 WMM; WP04 bituminous; WP05 Zone A-2)
        seed("N03-A001", "Mobilisation & Site Establishment — Dholera SIR Zone A",
                wbs.get("DMIC-N03-P01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-07-01", "2024-08-15", "2024-07-01", "2024-08-20",
                45.0, 0.0, 100.0, 0.0, true, ActivityStatus.COMPLETED, order++,
                "EVM (₹ Lakh) — BCWS 285 / BCWP 285 / ACWP 295");
        seed("N03-A002", "Topographic Survey & Ground Investigation Zone A-1",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-07-01", "2024-07-31", "2024-07-01", "2024-08-02",
                30.0, 0.0, 100.0, 0.0, true, ActivityStatus.COMPLETED, order++,
                "EVM (₹ Lakh) — BCWS 48 / BCWP 48 / ACWP 51");
        seed("N03-A003", "Soil Testing & Geotechnical Report (25 boreholes)",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-07-15", "2024-08-14", "2024-07-15", "2024-08-22",
                30.0, 0.0, 100.0, 2.0, false, ActivityStatus.COMPLETED, order++,
                "EVM (₹ Lakh) — BCWS 22 / BCWP 22 / ACWP 24");
        seed("N03-A004", "Clearing & Grubbing Zone A-1 (580 Hectares)",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-08-01", "2024-09-30", "2024-08-01", "2024-10-05",
                60.0, 0.0, 100.0, 0.0, true, ActivityStatus.COMPLETED, order++,
                "EVM (₹ Lakh) — BCWS 125 / BCWP 125 / ACWP 130");
        seed("N03-A005", "Earthwork Excavation — Zone A-1 Ch 0+000 to Ch 5+000",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-09-01", "2024-11-30", "2024-09-01", null,
                90.0, 12.0, 87.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 485 / BCWP 422 / ACWP 440  (SPI 0.87, CPI 0.96)");
        seed("N03-A006", "Earthwork Embankment — Zone A-1 (1.2 M Cum Fill)",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-10-01", "2025-01-31", "2024-10-01", null,
                120.0, 35.0, 71.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 620 / BCWP 440 / ACWP 462  (SPI 0.71)");
        seed("N03-A007", "Compaction & Proof Rolling Zone A-1",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-11-01", "2025-02-28", "2024-11-10", null,
                119.0, 55.0, 54.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 168 / BCWP 91 / ACWP 95");
        seed("N03-A008", "Formation Level Checking & FRL Correction",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-12-01", "2025-03-31", null, null,
                120.0, 120.0, 0.0, 8.0, false, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 45 / BCWP 0 / ACWP 0");
        seed("N03-A009", "Granular Sub-Base (GSB) Zone A-1 — 350mm thick",
                wbs.get("DMIC-N03-P01-WP02"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-11-15", "2025-03-14", "2024-11-20", null,
                119.0, 62.0, 48.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 385 / BCWP 185 / ACWP 195");
        seed("N03-A010", "Wet Mix Macadam (WMM) Layer Zone A-1 — 250mm",
                wbs.get("DMIC-N03-P01-WP03"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-03-01", "2025-06-30", null, null,
                121.0, 121.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 420 / BCWP 0 / ACWP 0");
        seed("N03-A011", "Dense Bituminous Macadam (DBM) Zone A-1",
                wbs.get("DMIC-N03-P01-WP04"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-06-01", "2025-08-31", null, null,
                91.0, 91.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 380 / BCWP 0 / ACWP 0");
        seed("N03-A012", "Bituminous Concrete (BC) Surface Course Zone A-1",
                wbs.get("DMIC-N03-P01-WP04"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-09-01", "2025-10-31", null, null,
                60.0, 60.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 210 / BCWP 0 / ACWP 0");
        seed("N03-A013", "Road Markings, Kerbs & Street Furniture Zone A-1",
                wbs.get("DMIC-N03-P01-WP04"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-11-01", "2025-11-30", null, null,
                30.0, 30.0, 0.0, 4.0, false, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 68 / BCWP 0 / ACWP 0");
        seed("N03-A014", "Earthwork Zone A-2 Ch 5+000 to Ch 10+000",
                wbs.get("DMIC-N03-P01-WP05"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-09-01", "2025-01-31", "2024-09-05", null,
                152.0, 38.0, 75.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 485 / BCWP 364 / ACWP 380");
        seed("N03-A015", "Sub-Base Zone A-2",
                wbs.get("DMIC-N03-P01-WP05"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-01-01", "2025-04-30", null, null,
                119.0, 119.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 365 / BCWP 0 / ACWP 0");

        // Package 2 — Water Supply & Treatment
        seed("N03-A016", "Intake Works & Raw Water Pumping Main",
                wbs.get("DMIC-N03-P02-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-09-01", "2025-02-28", "2024-09-05", null,
                180.0, 46.0, 74.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 485 / BCWP 359 / ACWP 375");
        seed("N03-A017", "WTP Civil Works — Clarifiers, Filters, Sludge Thickeners",
                wbs.get("DMIC-N03-P02-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-10-01", "2025-04-30", "2024-10-10", null,
                210.0, 72.0, 66.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 620 / BCWP 409 / ACWP 428");
        seed("N03-A018", "WTP Mechanical & Electrical Works",
                wbs.get("DMIC-N03-P02-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-03-01", "2025-06-30", null, null,
                121.0, 121.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 380 / BCWP 0 / ACWP 0");
        seed("N03-A019", "Water Transmission Main (DN 1200mm) — 0-10 km",
                wbs.get("DMIC-N03-P02-WP02"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-11-01", "2025-04-30", "2024-11-05", null,
                180.0, 60.0, 67.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 490 / BCWP 328 / ACWP 345");
        seed("N03-A020", "Water Transmission Main (DN 1200mm) — 10-28 km",
                wbs.get("DMIC-N03-P02-WP02"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-02-01", "2025-09-30", null, null,
                241.0, 241.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 710 / BCWP 0 / ACWP 0");

        // Package 3 — Power Distribution & Sub-station
        seed("N03-A021", "Sub-station Civil Works — Main Building & GIS Room",
                wbs.get("DMIC-N03-P03-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-05-01", "2024-10-31", "2024-05-01", "2024-11-05",
                183.0, 0.0, 100.0, 0.0, true, ActivityStatus.COMPLETED, order++,
                "EVM (₹ Lakh) — BCWS 485 / BCWP 485 / ACWP 498");
        seed("N03-A022", "220kV GIS Switchgear Supply & Erection",
                wbs.get("DMIC-N03-P03-WP02"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-10-01", "2025-01-31", "2024-10-05", null,
                122.0, 28.0, 77.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 620 / BCWP 477 / ACWP 492");
        seed("N03-A023", "220/33kV Power Transformer Supply & Erection (3×100MVA)",
                wbs.get("DMIC-N03-P03-WP02"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-11-01", "2025-02-28", "2024-11-10", null,
                119.0, 35.0, 71.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 810 / BCWP 575 / ACWP 595");
        seed("N03-A024", "33kV Switchgear, Busbar & Protection Systems",
                wbs.get("DMIC-N03-P03-WP02"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-01-01", "2025-03-31", null, null,
                90.0, 90.0, 0.0, 5.0, false, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 295 / BCWP 0 / ACWP 0");
        seed("N03-A025", "HV Testing & Commissioning 220kV Bay",
                wbs.get("DMIC-N03-P03-WP03"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-04-01", "2025-07-31", null, null,
                121.0, 121.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 180 / BCWP 0 / ACWP 0");

        // Package 4 — ICT / Smart City
        seed("N03-A026", "Fibre Optic Cable Laying — Backbone Ring Segment 1 (15 km)",
                wbs.get("DMIC-N03-P04-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2024-10-01", "2025-01-31", "2024-10-08", null,
                122.0, 32.0, 74.0, 0.0, true, ActivityStatus.IN_PROGRESS, order++,
                "EVM (₹ Lakh) — BCWS 285 / BCWP 211 / ACWP 225");
        seed("N03-A027", "Fibre Optic Cable Laying — Backbone Ring Segment 2 (15 km)",
                wbs.get("DMIC-N03-P04-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-01-01", "2025-04-30", null, null,
                119.0, 119.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 285 / BCWP 0 / ACWP 0");
        seed("N03-A028", "Fibre Optic Cable Laying — Backbone Ring Segment 3 (15 km)",
                wbs.get("DMIC-N03-P04-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-04-01", "2025-07-31", null, null,
                121.0, 121.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 285 / BCWP 0 / ACWP 0");
        seed("N03-A029", "OFC Joint Pits, Manholes & Surface Restoration",
                wbs.get("DMIC-N03-P04-WP01"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-08-01", "2025-09-30", null, null,
                61.0, 61.0, 0.0, 3.0, false, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 68 / BCWP 0 / ACWP 0");
        seed("N03-A030", "SCCC Building Civil Works",
                wbs.get("DMIC-N03-P04-WP02"), projectId, calendarId, ActivityType.TASK_DEPENDENT,
                "2025-04-01", "2025-12-31", null, null,
                274.0, 274.0, 0.0, 0.0, true, ActivityStatus.NOT_STARTED, order++,
                "EVM (₹ Lakh) — BCWS 485 / BCWP 0 / ACWP 0");

        // Key milestones (4)
        seedMilestone("N03-M001", "Milestone — Zone A-1 Earthwork Completion",
                wbs.get("DMIC-N03-P01-WP01"), projectId, calendarId,
                "2025-01-31", order++);
        seedMilestone("N03-M002", "Milestone — Sub-station 220kV Energisation",
                wbs.get("DMIC-N03-P03-WP03"), projectId, calendarId,
                "2025-07-31", order++);
        seedMilestone("N03-M003", "Milestone — WTP Commissioning (Phase 1)",
                wbs.get("DMIC-N03-P02-WP01"), projectId, calendarId,
                "2025-06-30", order++);
        seedMilestone("N03-M004", "Milestone — Trunk Road Zone A-1 Substantial Completion",
                wbs.get("DMIC-N03-P01"), projectId, calendarId,
                "2025-11-30", order++);

        long count = activityRepository.count();
        long critical = activityRepository.findAll().stream().filter(Activity::getIsCritical).count();
        log.info("[IC-PMS M2] seeded {} schedule activities ({} critical) for DMIC-N03 Dholera SIR", count, critical);
    }

    // ------------------------------------------------------------------ helpers

    private void seed(String code, String name, UUID wbsId, UUID projectId, UUID calendarId,
                      ActivityType type,
                      String plannedStart, String plannedFinish,
                      String actualStart, String actualFinish,
                      Double originalDuration, Double remainingDuration,
                      Double physicalPercent, Double totalFloat,
                      boolean isCritical, ActivityStatus status, int sortOrder,
                      String notes) {
        if (wbsId == null) {
            log.warn("[IC-PMS M2] WBS FK missing for activity {} — skipping", code);
            return;
        }
        Activity a = new Activity();
        a.setCode(code);
        a.setName(name);
        a.setProjectId(projectId);
        a.setWbsNodeId(wbsId);
        a.setCalendarId(calendarId);
        a.setActivityType(type);
        a.setDurationType(DurationType.FIXED_DURATION_AND_UNITS);
        a.setPercentCompleteType(PercentCompleteType.PHYSICAL);
        a.setStatus(status);
        a.setOriginalDuration(originalDuration);
        a.setRemainingDuration(remainingDuration);
        a.setAtCompletionDuration(originalDuration);
        a.setPlannedStartDate(LocalDate.parse(plannedStart));
        a.setPlannedFinishDate(LocalDate.parse(plannedFinish));
        a.setEarlyStartDate(LocalDate.parse(plannedStart));
        a.setEarlyFinishDate(LocalDate.parse(plannedFinish));
        if (actualStart != null) a.setActualStartDate(LocalDate.parse(actualStart));
        if (actualFinish != null) a.setActualFinishDate(LocalDate.parse(actualFinish));
        a.setTotalFloat(totalFloat);
        a.setFreeFloat(totalFloat); // simplified — real scheduler would compute separately
        a.setPercentComplete(physicalPercent);
        a.setPhysicalPercentComplete(physicalPercent);
        a.setDurationPercentComplete(physicalPercent);
        a.setIsCritical(isCritical);
        a.setSortOrder(sortOrder);
        a.setNotes(notes);
        activityRepository.save(a);
    }

    private void seedMilestone(String code, String name, UUID wbsId, UUID projectId,
                               UUID calendarId, String date, int sortOrder) {
        if (wbsId == null) {
            log.warn("[IC-PMS M2] WBS FK missing for milestone {} — skipping", code);
            return;
        }
        Activity a = new Activity();
        a.setCode(code);
        a.setName(name);
        a.setProjectId(projectId);
        a.setWbsNodeId(wbsId);
        a.setCalendarId(calendarId);
        a.setActivityType(ActivityType.FINISH_MILESTONE);
        a.setDurationType(DurationType.FIXED_DURATION_AND_UNITS);
        a.setPercentCompleteType(PercentCompleteType.PHYSICAL);
        a.setStatus(ActivityStatus.NOT_STARTED);
        a.setOriginalDuration(0.0);
        a.setRemainingDuration(0.0);
        a.setAtCompletionDuration(0.0);
        a.setPlannedStartDate(LocalDate.parse(date));
        a.setPlannedFinishDate(LocalDate.parse(date));
        a.setEarlyStartDate(LocalDate.parse(date));
        a.setEarlyFinishDate(LocalDate.parse(date));
        a.setTotalFloat(0.0);
        a.setFreeFloat(0.0);
        a.setPercentComplete(0.0);
        a.setIsCritical(true);
        a.setSortOrder(sortOrder);
        activityRepository.save(a);
    }
}
