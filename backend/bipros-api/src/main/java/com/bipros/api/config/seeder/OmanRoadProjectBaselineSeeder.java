package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.domain.BaselineRelationship;
import com.bipros.baseline.domain.BaselineType;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRelationshipRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seeds {@code BL1 — Original Baseline} for the Oman Barka–Nakhal Road project
 * (code {@code 6155}).
 *
 * <p>Mirrors the NHAI baseline pattern — captures the live planned dates with a
 * deterministic slip / on-track / ahead pattern (modulo 3) so the Variance Report
 * has visible drift to demo without further data entry:
 * <ul>
 *   <li>Every 3rd activity (i % 3 == 0): on-track — 0 day variance.</li>
 *   <li>Every 3rd + 1 (i % 3 == 1): late — baseline pulled BACK 7..21 days
 *       so the live dates appear delayed against the baseline.</li>
 *   <li>Every 3rd + 2 (i % 3 == 2): ahead — baseline pushed FORWARD 3..9 days
 *       so the live dates appear ahead.</li>
 * </ul>
 *
 * <p>Sentinel: {@code baselineRepository.countByProjectId(projectId) > 0}.
 *
 * <p>Order: 161 — runs after {@link OmanRoadProjectSeeder} (141) and the supplemental
 * seeder (151) so all activities + expenses + assignments resolve.
 */
@Slf4j
@Component
@Profile("seed")
@Order(161)
@RequiredArgsConstructor
public class OmanRoadProjectBaselineSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";

    private final BaselineRepository baselineRepository;
    private final BaselineActivityRepository baselineActivityRepository;
    private final BaselineRelationshipRepository baselineRelationshipRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRelationshipRepository activityRelationshipRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final ProjectRepository projectRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.info("[BNK-BASELINE] project '{}' not seeded — skipping baseline", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (baselineRepository.countByProjectId(projectId) > 0) {
            log.info("[BNK-BASELINE] baseline already present for {}, skipping", PROJECT_CODE);
            return;
        }

        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.isEmpty()) {
            log.warn("[BNK-BASELINE] no activities found for {} — skipping", PROJECT_CODE);
            return;
        }

        // Stable ordering so the variance pattern is reproducible across reboots.
        activities.sort(Comparator.comparing(Activity::getPlannedStartDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Activity::getId));

        Map<UUID, List<ActivityExpense>> expensesByActivity = activityExpenseRepository
                .findByProjectId(projectId).stream()
                .filter(e -> e.getActivityId() != null)
                .collect(Collectors.groupingBy(ActivityExpense::getActivityId));
        Map<UUID, List<ResourceAssignment>> assignmentsByActivity = resourceAssignmentRepository
                .findByProjectId(projectId).stream()
                .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

        BigDecimal totalCost = BigDecimal.ZERO;
        LocalDate earliestStart = null;
        LocalDate latestFinish = null;
        List<BaselineActivity> staged = new ArrayList<>(activities.size());

        for (int i = 0; i < activities.size(); i++) {
            Activity a = activities.get(i);
            int driftDays = computeDriftDays(i);

            LocalDate baselineStart = a.getPlannedStartDate() != null
                    ? a.getPlannedStartDate().minusDays(driftDays)
                    : null;
            LocalDate baselineFinish = a.getPlannedFinishDate() != null
                    ? a.getPlannedFinishDate().minusDays(driftDays)
                    : null;

            BigDecimal planned = sumPlanned(a, expensesByActivity, assignmentsByActivity);
            BigDecimal actual = sumActual(a, expensesByActivity, assignmentsByActivity);

            BaselineActivity ba = new BaselineActivity();
            ba.setActivityId(a.getId());
            ba.setEarlyStart(baselineStart);
            ba.setEarlyFinish(baselineFinish);
            ba.setLateStart(baselineStart);
            ba.setLateFinish(baselineFinish);
            ba.setOriginalDuration(a.getOriginalDuration());
            ba.setRemainingDuration(a.getOriginalDuration());
            ba.setTotalFloat(a.getTotalFloat());
            ba.setFreeFloat(a.getFreeFloat());
            ba.setPlannedCost(planned);
            ba.setActualCost(actual);
            ba.setPercentComplete(0.0);
            staged.add(ba);

            totalCost = totalCost.add(planned);
            if (baselineStart != null && (earliestStart == null || baselineStart.isBefore(earliestStart))) {
                earliestStart = baselineStart;
            }
            if (baselineFinish != null && (latestFinish == null || baselineFinish.isAfter(latestFinish))) {
                latestFinish = baselineFinish;
            }
        }

        Baseline bl1 = new Baseline();
        bl1.setProjectId(projectId);
        bl1.setName("BL1 — Original Baseline");
        bl1.setDescription("Frozen reference plan for project " + PROJECT_CODE
                + " (Barka–Nakhal Road dualisation). Used as the source-of-truth for "
                + "schedule + cost variance against the live programme.");
        bl1.setBaselineType(BaselineType.PRIMARY);
        // baselineDate = project plannedStartDate (per plan)
        bl1.setBaselineDate(project.getPlannedStartDate() != null
                ? project.getPlannedStartDate()
                : LocalDate.of(2024, 9, 1));
        bl1.setIsActive(true);
        bl1.setTotalActivities(activities.size());
        bl1.setTotalCost(totalCost);
        bl1.setProjectStartDate(earliestStart);
        bl1.setProjectFinishDate(latestFinish);
        bl1.setProjectDuration(earliestStart != null && latestFinish != null
                ? (double) ChronoUnit.DAYS.between(earliestStart, latestFinish)
                : 0.0);
        Baseline saved = baselineRepository.save(bl1);

        for (BaselineActivity ba : staged) {
            ba.setBaselineId(saved.getId());
            baselineActivityRepository.save(ba);
        }

        // ── Baseline relationships ──
        List<ActivityRelationship> rels = activityRelationshipRepository.findByProjectId(projectId);
        int relCount = 0;
        for (ActivityRelationship r : rels) {
            BaselineRelationship br = new BaselineRelationship();
            br.setBaselineId(saved.getId());
            br.setPredecessorActivityId(r.getPredecessorActivityId());
            br.setSuccessorActivityId(r.getSuccessorActivityId());
            br.setRelationshipType(r.getRelationshipType() != null
                    ? r.getRelationshipType().name() : null);
            br.setLag(r.getLag());
            baselineRelationshipRepository.save(br);
            relCount++;
        }

        // Mark BL1 as the project's active baseline so /reports/variance defaults to it.
        project.setActiveBaselineId(saved.getId());
        projectRepository.save(project);

        log.info("[BNK-BASELINE] seeded BL1 for {} with {} activity snapshots + {} relationships (active)",
                PROJECT_CODE, staged.size(), relCount);

        // ── BL2 — Re-forecast 2026-Q1 (skewed +8% on remaining work) ──
        BigDecimal bl2TotalCost = BigDecimal.ZERO;
        LocalDate bl2EarliestStart = null;
        LocalDate bl2LatestFinish = null;
        List<BaselineActivity> bl2Staged = new ArrayList<>(activities.size());

        for (int i = 0; i < activities.size(); i++) {
            Activity a = activities.get(i);
            int driftDays = computeDriftDays(i);

            LocalDate baselineStart = a.getPlannedStartDate() != null
                    ? a.getPlannedStartDate().minusDays(driftDays)
                    : null;
            LocalDate baselineFinish = a.getPlannedFinishDate() != null
                    ? a.getPlannedFinishDate().minusDays(driftDays)
                    : null;

            // BL2: remainingDuration × 1.08 (8% skew on remaining work)
            double bl2Remaining = a.getOriginalDuration() != null
                    ? a.getOriginalDuration() * 1.08
                    : 0.0;

            BigDecimal planned = sumPlanned(a, expensesByActivity, assignmentsByActivity);
            BigDecimal actual = sumActual(a, expensesByActivity, assignmentsByActivity);

            BaselineActivity ba = new BaselineActivity();
            ba.setActivityId(a.getId());
            ba.setEarlyStart(baselineStart);
            ba.setEarlyFinish(baselineStart != null && a.getOriginalDuration() != null
                    ? baselineStart.plusDays((long) bl2Remaining) : baselineFinish);
            ba.setLateStart(baselineStart);
            ba.setLateFinish(ba.getEarlyFinish());
            ba.setOriginalDuration(a.getOriginalDuration());
            ba.setRemainingDuration(bl2Remaining);
            ba.setTotalFloat(a.getTotalFloat());
            ba.setFreeFloat(a.getFreeFloat());
            ba.setPlannedCost(planned);
            ba.setActualCost(actual);
            ba.setPercentComplete(0.0);
            bl2Staged.add(ba);

            bl2TotalCost = bl2TotalCost.add(planned);
            if (baselineStart != null && (bl2EarliestStart == null || baselineStart.isBefore(bl2EarliestStart))) {
                bl2EarliestStart = baselineStart;
            }
            if (ba.getEarlyFinish() != null && (bl2LatestFinish == null || ba.getEarlyFinish().isAfter(bl2LatestFinish))) {
                bl2LatestFinish = ba.getEarlyFinish();
            }
        }

        Baseline bl2 = new Baseline();
        bl2.setProjectId(projectId);
        bl2.setName("BL2 — Re-forecast 2026-Q1");
        bl2.setDescription("Re-forecast baseline for project " + PROJECT_CODE
                + " (Barka–Nakhal Road dualisation). Remaining durations skewed +8% "
                + "to reflect Q1-2026 re-forecast expectations.");
        bl2.setBaselineType(BaselineType.SECONDARY);
        bl2.setBaselineDate(LocalDate.of(2026, 1, 1));
        bl2.setIsActive(false);
        bl2.setTotalActivities(activities.size());
        bl2.setTotalCost(bl2TotalCost);
        bl2.setProjectStartDate(bl2EarliestStart);
        bl2.setProjectFinishDate(bl2LatestFinish);
        bl2.setProjectDuration(bl2EarliestStart != null && bl2LatestFinish != null
                ? (double) ChronoUnit.DAYS.between(bl2EarliestStart, bl2LatestFinish)
                : 0.0);
        Baseline bl2Saved = baselineRepository.save(bl2);

        for (BaselineActivity ba : bl2Staged) {
            ba.setBaselineId(bl2Saved.getId());
            baselineActivityRepository.save(ba);
        }

        // BL2 relationships (same topology as BL1)
        int bl2RelCount = 0;
        for (ActivityRelationship r : rels) {
            BaselineRelationship br = new BaselineRelationship();
            br.setBaselineId(bl2Saved.getId());
            br.setPredecessorActivityId(r.getPredecessorActivityId());
            br.setSuccessorActivityId(r.getSuccessorActivityId());
            br.setRelationshipType(r.getRelationshipType() != null
                    ? r.getRelationshipType().name() : null);
            br.setLag(r.getLag());
            baselineRelationshipRepository.save(br);
            bl2RelCount++;
        }

        log.info("[BNK-BASELINE] seeded BL2 for {} with {} activity snapshots + {} relationships (inactive, +8% remaining skew)",
                PROJECT_CODE, bl2Staged.size(), bl2RelCount);
    }

    private static int computeDriftDays(int index) {
        int bucket = index % 3;
        if (bucket == 0) return 0;            // on-track
        if (bucket == 1) return 7 + (index % 15);   // late: 7..21 days slipped (baseline back)
        return -(3 + (index % 7));                  // ahead: 3..9 days early
    }

    private static BigDecimal sumPlanned(Activity activity,
                                         Map<UUID, List<ActivityExpense>> expenses,
                                         Map<UUID, List<ResourceAssignment>> assignments) {
        BigDecimal sum = BigDecimal.ZERO;
        List<ActivityExpense> es = expenses.get(activity.getId());
        if (es != null) {
            for (ActivityExpense e : es) if (e.getBudgetedCost() != null) sum = sum.add(e.getBudgetedCost());
        }
        List<ResourceAssignment> as = assignments.get(activity.getId());
        if (as != null) {
            for (ResourceAssignment a : as) if (a.getPlannedCost() != null) sum = sum.add(a.getPlannedCost());
        }
        return sum;
    }

    private static BigDecimal sumActual(Activity activity,
                                        Map<UUID, List<ActivityExpense>> expenses,
                                        Map<UUID, List<ResourceAssignment>> assignments) {
        BigDecimal sum = BigDecimal.ZERO;
        List<ActivityExpense> es = expenses.get(activity.getId());
        if (es != null) {
            for (ActivityExpense e : es) if (e.getActualCost() != null) sum = sum.add(e.getActualCost());
        }
        List<ResourceAssignment> as = assignments.get(activity.getId());
        if (as != null) {
            for (ResourceAssignment a : as) if (a.getActualCost() != null) sum = sum.add(a.getActualCost());
        }
        return sum;
    }
}
