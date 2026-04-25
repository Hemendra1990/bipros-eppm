package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.domain.BaselineType;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * IC-PMS M2 GAP — baseline snapshot for DMIC-PROG.
 *
 * <p>Seeds a single {@code "BL1 — Initial Baseline"} on the DMIC-PROG programme, then
 * copies the current {@link Activity} roster into {@link BaselineActivity} rows so the
 * baseline variance panels have data. BL1 represents the frozen plan at contract award
 * (data date 2024-04-01) — all baseline activity rows carry the ORIGINAL planned dates,
 * original-duration and 0% progress, regardless of the live status each activity has
 * moved on to.
 *
 * <p>Field-name reality check against entities:
 * <ul>
 *   <li>{@code Baseline} exposes {@code baselineType} ({@link BaselineType} — no INITIAL
 *       value, we use {@link BaselineType#PRIMARY} as the canonical frozen baseline),
 *       {@code isActive} (frozen baselines are still active/readable), and
 *       {@code baselineDate} (the snapshot data date). There is no {@code snapshotTakenAt},
 *       {@code status} or {@code isDefault} — JPA auditing covers the timestamp.</li>
 *   <li>{@code BaselineActivity} stores {@code earlyStart/earlyFinish} (copy of planned
 *       dates), {@code originalDuration}, {@code remainingDuration=originalDuration}
 *       (nothing done yet on BL1) and {@code percentComplete=0.0}. It does NOT copy
 *       {@code activityCode} / {@code wbsNodeId} — those stay resolvable via
 *       {@code activityId} FK to the activity row.</li>
 * </ul>
 *
 * <p>Sentinel: {@code baselineRepository.count() > 0} — any pre-existing baseline skips.
 */
@Slf4j
@Component
@Profile("dev")
@Order(120)
@RequiredArgsConstructor
public class IcpmsBaselineSeeder implements CommandLineRunner {

    private final BaselineRepository baselineRepository;
    private final BaselineActivityRepository baselineActivityRepository;
    private final ActivityRepository activityRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Project project = projectRepository.findByCode("DMIC-PROG")
                .orElseThrow(() -> new IllegalStateException("DMIC-PROG project not seeded — run Phase A first"));
        UUID projectId = project.getId();

        // Per-project sentinel — global count is unsafe now that other seeders (NHAI Road)
        // also create baselines.
        if (baselineRepository.countByProjectId(projectId) > 0) {
            log.info("[IC-PMS M2 GAP] baselines already present for DMIC-PROG, skipping");
            return;
        }

        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.isEmpty()) {
            log.warn("[IC-PMS M2 GAP] no activities found for DMIC-PROG — skipping baseline seed");
            return;
        }

        // Compute programme span + total original duration from the live activity set.
        LocalDate earliestStart = activities.stream()
                .map(Activity::getPlannedStartDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.of(2024, 4, 1));
        LocalDate latestFinish = activities.stream()
                .map(Activity::getPlannedFinishDate)
                .filter(d -> d != null)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.of(2025, 12, 31));
        double totalDuration = activities.stream()
                .map(Activity::getOriginalDuration)
                .filter(d -> d != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        Baseline bl1 = new Baseline();
        bl1.setProjectId(projectId);
        bl1.setName("BL1 — Initial Baseline");
        bl1.setDescription("Frozen baseline approved at contract award (2024-04-01). Represents the original plan " +
                "for the 34-activity DMIC-N03 Dholera SIR schedule — used as the reference for SPI/SV drift.");
        bl1.setBaselineType(BaselineType.PRIMARY);
        bl1.setBaselineDate(LocalDate.of(2024, 4, 1));
        bl1.setIsActive(true);
        bl1.setTotalActivities(activities.size());
        // ₹22,000 cr = 22,000 × 10,000,000 = 220,000,000,000 (₹2.2 × 10^11) — matches N03 WBS total.
        bl1.setTotalCost(new BigDecimal("22000").multiply(new BigDecimal("10000000")));
        bl1.setProjectDuration(totalDuration);
        bl1.setProjectStartDate(earliestStart);
        bl1.setProjectFinishDate(latestFinish);
        baselineRepository.save(bl1);

        int count = 0;
        for (Activity a : activities) {
            BaselineActivity ba = new BaselineActivity();
            ba.setBaselineId(bl1.getId());
            ba.setActivityId(a.getId());
            // BL1 snapshot = original plan — copy planned dates, zero progress.
            ba.setEarlyStart(a.getPlannedStartDate());
            ba.setEarlyFinish(a.getPlannedFinishDate());
            ba.setLateStart(a.getPlannedStartDate());
            ba.setLateFinish(a.getPlannedFinishDate());
            ba.setOriginalDuration(a.getOriginalDuration());
            ba.setRemainingDuration(a.getOriginalDuration()); // BL1 was untouched at creation
            ba.setTotalFloat(a.getTotalFloat());
            ba.setFreeFloat(a.getFreeFloat());
            ba.setPercentComplete(0.0); // BL1 — initial plan had no work done
            baselineActivityRepository.save(ba);
            count++;
        }

        // Mark BL1 as DMIC-PROG's active baseline so the variance reports default to it.
        project.setActiveBaselineId(bl1.getId());
        projectRepository.save(project);

        log.info("[IC-PMS M2 GAP] seeded BL1 Initial Baseline for DMIC-PROG with {} activity snapshots (active)", count);
    }
}
