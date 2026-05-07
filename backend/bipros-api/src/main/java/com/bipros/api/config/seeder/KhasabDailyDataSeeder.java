package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.model.DprMaterial;
import com.bipros.project.domain.model.EquipmentAvailability;
import com.bipros.project.domain.model.EquipmentOwnership;
import com.bipros.project.domain.model.ManpowerCategory;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.SafetyIncidentType;
import com.bipros.project.domain.model.Shift;
import com.bipros.project.domain.model.Side;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Khasab–Daba Asphalt Road Project (SC-180) — daily-operations seeder. Produces realistic DPR
 * rows with full manpower / equipment / material child collections, modelled on the customer's
 * handwritten DPR sheets for Jan–Mar 2026.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Find a project with code starting with "SC-180" or "SC180" or name containing "Khasab".
 *       If none, create a minimal Project record so the AI demo has something to point at.</li>
 *   <li>Skip entirely if any DPR rows already exist for the project (idempotent re-runs).</li>
 *   <li>Seed 60 days of working-day DPRs (Sun–Thu, Oman work week) with rich resource detail.</li>
 * </ol>
 *
 * <p>Profile-gated to {@code seed} only — never runs in prod.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("seed")
@Order(180)
public class KhasabDailyDataSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "SC-180";
    private static final String PROJECT_NAME = "SC 180 — Khasab–Daba Asphalt Road & Link to Lima";

    // Activities mirror the handwritten DPR sheet from the customer.
    private static final List<KhasabActivity> ACTIVITY_DEFS = List.of(
            new KhasabActivity("2.3.6(i)", "Excavator Screening Work", "Cum"),
            new KhasabActivity("2.3.6(i)a", "Bench Cutting", "Cum"),
            new KhasabActivity("2.3.6(i)b", "Box Culvert Relaying", "R/mtr"),
            new KhasabActivity("2.3.6(ii)", "Rip Rap Work", "Cum"),
            new KhasabActivity("2.3.6(iii)", "Concrete Ditch Bed Preparation", "Sqm"),
            new KhasabActivity("2.3.6(iv)", "Rip Rap Stone Shifting", "Cum")
    );

    private static final List<String> SUPERVISORS = List.of(
            "Mohd Ismaila", "Anand Kumar", "Sharafudheen", "Manoj K"
    );
    private static final List<String> CONTRACTORS = List.of("Sandou Construction", "AlBahar Civil");
    private static final List<String> WEATHER = List.of("Clear", "Hot", "Clear", "Cloudy", "Hot");

    private final ProjectRepository projectRepository;
    private final ActivityRepository activityRepository;
    private final DailyProgressReportRepository dprRepository;
    private final DprManpowerRepository manpowerRepository;
    private final DprEquipmentRepository equipmentRepository;
    private final DprMaterialRepository materialRepository;

    @Override
    public void run(String... args) {
        Project project = findOrCreateProject();
        if (project == null) {
            log.warn("Khasab seeder skipped: project create failed");
            return;
        }

        List<DailyProgressReport> existing =
                dprRepository.findByProjectIdOrderByReportDateAscIdAsc(project.getId());
        if (!existing.isEmpty()) {
            log.info("Khasab seeder skipped: {} DPRs already exist for {}",
                    existing.size(), project.getCode());
            return;
        }

        List<Activity> activities = ensureActivities(project.getId());
        if (activities.isEmpty()) {
            log.warn("Khasab seeder skipped: no activities for project {}", project.getCode());
            return;
        }

        Random rng = new Random(180_180L);
        LocalDate end = LocalDate.of(2026, 3, 31);
        LocalDate start = end.minusDays(60);

        int dprCount = 0;
        int mpCount = 0;
        int eqCount = 0;
        int matCount = 0;

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            DayOfWeek dow = day.getDayOfWeek();
            if (dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY) continue;

            // 2–4 activities per day
            int activitiesToday = 2 + rng.nextInt(3);
            for (int i = 0; i < activitiesToday; i++) {
                Activity act = activities.get(rng.nextInt(activities.size()));
                KhasabActivity def = ACTIVITY_DEFS.stream()
                        .filter(a -> a.activityName().equalsIgnoreCase(act.getName()))
                        .findFirst()
                        .orElse(ACTIVITY_DEFS.get(0));

                DailyProgressReport dpr = buildDpr(project.getId(), act, def, day, rng);
                DailyProgressReport saved = dprRepository.save(dpr);
                dprCount++;

                List<DprManpower> mp = buildManpower(saved.getId(), rng);
                manpowerRepository.saveAll(mp);
                mpCount += mp.size();

                List<DprEquipment> eq = buildEquipment(saved.getId(), def, rng);
                equipmentRepository.saveAll(eq);
                eqCount += eq.size();

                if (rng.nextInt(3) == 0) {
                    List<DprMaterial> mat = buildMaterial(saved.getId(), def, rng);
                    materialRepository.saveAll(mat);
                    matCount += mat.size();
                }
            }
        }

        log.info("Khasab seeder created project={} ({}) activities={} dprs={} manpower={} equipment={} material={}",
                project.getCode(), project.getId(), activities.size(),
                dprCount, mpCount, eqCount, matCount);
    }

    private Project findOrCreateProject() {
        Optional<Project> match = projectRepository.findAll().stream()
                .filter(p -> p.getCode() != null
                        && (p.getCode().equalsIgnoreCase(PROJECT_CODE)
                            || p.getCode().equalsIgnoreCase("SC180")))
                .findFirst();
        if (match.isPresent()) return match.get();

        Optional<Project> byName = projectRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().toLowerCase().contains("khasab"))
                .findFirst();
        if (byName.isPresent()) return byName.get();

        Project p = new Project();
        p.setCode(PROJECT_CODE);
        p.setName(PROJECT_NAME);
        p.setDescription("Design and Construction of Khasab–Daba Asphalt Road and Link to Lima. "
                + "DEMO PROJECT — daily progress data is SYNTHETIC (deterministic-seeded), "
                + "patterned on the customer's handwritten DPR sheets but not parsed from the "
                + "Khasab Excel. Use for AI chat testing of resource-breakdown queries; do not "
                + "quote numbers as real customer figures.");
        p.setPlannedStartDate(LocalDate.of(2025, 11, 1));
        p.setPlannedFinishDate(LocalDate.of(2027, 6, 30));
        p.setDataDate(LocalDate.of(2026, 3, 31));
        p.setStatus(com.bipros.project.domain.model.ProjectStatus.ACTIVE);
        p.setCategory("HIGHWAY");
        p.setFromLocation("Khasab");
        p.setToLocation("Daba");
        p.setFromChainageM(0L);
        p.setToChainageM(8000L);
        p.setTotalLengthKm(BigDecimal.valueOf(8.0));
        try {
            return projectRepository.save(p);
        } catch (Exception e) {
            log.error("Failed to create Khasab project: {}", e.getMessage());
            return null;
        }
    }

    private List<Activity> ensureActivities(UUID projectId) {
        List<Activity> existing = activityRepository.findByProjectId(projectId);
        if (existing.size() >= ACTIVITY_DEFS.size()) {
            // Project already has enough activities — reuse those whose names match our defs.
            List<Activity> matched = new ArrayList<>();
            for (KhasabActivity def : ACTIVITY_DEFS) {
                existing.stream()
                        .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase(def.activityName()))
                        .findFirst()
                        .ifPresent(matched::add);
            }
            if (!matched.isEmpty()) return matched;
        }

        List<Activity> created = new ArrayList<>();
        for (KhasabActivity def : ACTIVITY_DEFS) {
            boolean alreadyThere = existing.stream()
                    .anyMatch(a -> a.getName() != null && a.getName().equalsIgnoreCase(def.activityName()));
            if (alreadyThere) continue;
            Activity a = new Activity();
            a.setProjectId(projectId);
            a.setCode(def.code());
            a.setName(def.activityName());
            a.setStatus(com.bipros.activity.domain.model.ActivityStatus.IN_PROGRESS);
            try {
                created.add(activityRepository.save(a));
            } catch (Exception e) {
                log.warn("Skipped activity {} on Khasab seed: {}", def.code(), e.getMessage());
            }
        }
        existing = activityRepository.findByProjectId(projectId);
        return existing.stream()
                .filter(a -> ACTIVITY_DEFS.stream()
                        .anyMatch(def -> def.activityName().equalsIgnoreCase(a.getName())))
                .toList();
    }

    private DailyProgressReport buildDpr(
            UUID projectId, Activity activity, KhasabActivity def, LocalDate day, Random rng) {
        long chainageFrom = 4000L + rng.nextInt(40) * 100L;
        long chainageTo = chainageFrom + 100L + rng.nextInt(5) * 100L;
        Side side = Side.values()[rng.nextInt(Side.values().length)];
        BigDecimal qty = BigDecimal.valueOf(20 + rng.nextInt(180))
                .setScale(2, RoundingMode.HALF_UP);
        return DailyProgressReport.builder()
                .projectId(projectId)
                .reportDate(day)
                .supervisorName(SUPERVISORS.get(rng.nextInt(SUPERVISORS.size())))
                .chainageFromM(chainageFrom)
                .chainageToM(chainageTo)
                .activityName(activity.getName())
                .unit(def.unit())
                .qtyExecuted(qty)
                .weatherCondition(WEATHER.get(rng.nextInt(WEATHER.size())))
                .side(side)
                .landmark("Khasab–Daba carriageway (synthetic demo data)")
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(17, 0))
                .shift(Shift.DAY)
                .approvalStatus(rng.nextInt(4) == 0 ? DprApprovalStatus.SUBMITTED : DprApprovalStatus.APPROVED)
                .contractorName(CONTRACTORS.get(rng.nextInt(CONTRACTORS.size())))
                .safetyIncidentType(SafetyIncidentType.NONE)
                .build();
    }

    private List<DprManpower> buildManpower(UUID dprId, Random rng) {
        List<DprManpower> out = new ArrayList<>();
        out.add(DprManpower.builder()
                .dprId(dprId)
                .trade("Operator")
                .category(ManpowerCategory.SKILLED)
                .nos(1)
                .workingHours(BigDecimal.valueOf(11.0))
                .otHours(BigDecimal.ZERO)
                .build());
        out.add(DprManpower.builder()
                .dprId(dprId)
                .trade("Helper")
                .category(ManpowerCategory.UNSKILLED)
                .nos(2 + rng.nextInt(4))
                .workingHours(BigDecimal.valueOf(11.0))
                .otHours(BigDecimal.valueOf(rng.nextInt(3)))
                .build());
        if (rng.nextInt(3) == 0) {
            out.add(DprManpower.builder()
                    .dprId(dprId)
                    .trade("Mason")
                    .category(ManpowerCategory.SEMI_SKILLED)
                    .nos(1 + rng.nextInt(2))
                    .workingHours(BigDecimal.valueOf(11.0))
                    .otHours(BigDecimal.ZERO)
                    .build());
        }
        if (rng.nextInt(4) == 0) {
            out.add(DprManpower.builder()
                    .dprId(dprId)
                    .trade("Foreman")
                    .category(ManpowerCategory.SKILLED)
                    .nos(1)
                    .workingHours(BigDecimal.valueOf(11.0))
                    .otHours(BigDecimal.ZERO)
                    .build());
        }
        return out;
    }

    private List<DprEquipment> buildEquipment(UUID dprId, KhasabActivity def, Random rng) {
        List<DprEquipment> out = new ArrayList<>();
        // Activity-specific equipment mix.
        if (def.activityName().contains("Excavator") || def.activityName().contains("Bench")
                || def.activityName().contains("Box Culvert")) {
            out.add(equip(dprId, "Excavator", "Exc-" + (38 + rng.nextInt(10)),
                    8 + rng.nextInt(3), rng));
        }
        if (def.activityName().contains("Rip Rap") || def.activityName().contains("Stone")) {
            out.add(equip(dprId, "Wheel Loader", "W/L-" + (50 + rng.nextInt(20)),
                    7 + rng.nextInt(3), rng));
            out.add(equip(dprId, "Tipper", "Tipper-" + (100 + rng.nextInt(20)),
                    9 + rng.nextInt(3), rng));
        }
        if (def.activityName().contains("Concrete")) {
            out.add(equip(dprId, "Mixer", "Mixer-" + (20 + rng.nextInt(10)),
                    6 + rng.nextInt(3), rng));
            out.add(equip(dprId, "JCB", "JCB-" + (12 + rng.nextInt(10)),
                    7 + rng.nextInt(3), rng));
        }
        if (out.isEmpty()) {
            out.add(equip(dprId, "Excavator", "Exc-" + (38 + rng.nextInt(10)),
                    8 + rng.nextInt(3), rng));
        }
        return out;
    }

    private DprEquipment equip(UUID dprId, String type, String fleet, int hours, Random rng) {
        return DprEquipment.builder()
                .dprId(dprId)
                .equipmentType(type)
                .fleetNo(fleet)
                .ownership(rng.nextInt(4) == 0 ? EquipmentOwnership.HIRED : EquipmentOwnership.OWNED)
                .nos(1)
                .workingHours(BigDecimal.valueOf(hours))
                .idleHours(BigDecimal.valueOf(rng.nextInt(2)))
                .breakdownHours(rng.nextInt(10) == 0 ? BigDecimal.valueOf(1) : BigDecimal.ZERO)
                .fuelLitres(BigDecimal.valueOf(20 + rng.nextInt(60)))
                .availabilityStatus(EquipmentAvailability.UTILIZED)
                .build();
    }

    private List<DprMaterial> buildMaterial(UUID dprId, KhasabActivity def, Random rng) {
        List<DprMaterial> out = new ArrayList<>();
        if (def.activityName().contains("Concrete")) {
            out.add(DprMaterial.builder()
                    .dprId(dprId)
                    .materialName("OPC Cement 53 Grade")
                    .quantity(BigDecimal.valueOf(50 + rng.nextInt(80)))
                    .unit("Bags")
                    .source("Yard Stock")
                    .vendorName("Oman Cement Co.")
                    .batchNo("OCC-" + (1000 + rng.nextInt(900)))
                    .build());
            out.add(DprMaterial.builder()
                    .dprId(dprId)
                    .materialName("Aggregate 20mm")
                    .quantity(BigDecimal.valueOf(8 + rng.nextInt(20)))
                    .unit("Cum")
                    .source("Khasab Quarry")
                    .build());
        } else if (def.activityName().contains("Rip Rap") || def.activityName().contains("Stone")) {
            out.add(DprMaterial.builder()
                    .dprId(dprId)
                    .materialName("Rip Rap Stone")
                    .quantity(BigDecimal.valueOf(30 + rng.nextInt(120)))
                    .unit("Cum")
                    .source("Daba Quarry")
                    .build());
        } else {
            out.add(DprMaterial.builder()
                    .dprId(dprId)
                    .materialName("Granular Sub-base")
                    .quantity(BigDecimal.valueOf(15 + rng.nextInt(40)))
                    .unit("Cum")
                    .source("Site Stockpile")
                    .build());
        }
        return out;
    }

    private record KhasabActivity(String code, String activityName, String unit) {}
}
