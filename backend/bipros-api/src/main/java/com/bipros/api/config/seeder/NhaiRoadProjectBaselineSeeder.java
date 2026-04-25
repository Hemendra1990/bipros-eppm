package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.domain.BaselineType;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seeds {@code BL1 — Initial Baseline} for the 4-Lane Highway / NH-48 (Rajasthan) project
 * (code {@code BIPROS/NHAI/RJ/2025/001}).
 *
 * <p>Unlike the IC-PMS seeder which captures the live planned dates verbatim (zero
 * variance out of the box), this seeder bakes a realistic mix of slip / on-track / ahead
 * activities so the Variance Report has visible drift to demonstrate without any further
 * data entry. Pattern (deterministic, by index after sorting on planned start):
 * <ul>
 *   <li>Every 3rd activity (i % 3 == 0): on-track — variance 0 days.</li>
 *   <li>Every 3rd + 1 (i % 3 == 1): late — baseline dates are pulled BACK by 7..21 days
 *       so the live dates appear delayed against the baseline.</li>
 *   <li>Every 3rd + 2 (i % 3 == 2): ahead — baseline dates are pushed FORWARD by 3..9 days
 *       so the live dates appear ahead.</li>
 * </ul>
 * Cost variance follows naturally from the live ResourceAssignment / ActivityExpense rows
 * the road-project SQL bundle already loads.
 *
 * <p>Sentinel: {@code baselineRepository.countByProjectId(projectId) > 0} (per-project, not
 * global, because other seeders also create baselines).
 *
 * <p>Order: 160 — runs after {@code NhaiRoadProjectSeeder} (140) which creates the project
 * and activities.
 */
@Slf4j
@Component
@Profile("dev")
@Order(160)
@RequiredArgsConstructor
public class NhaiRoadProjectBaselineSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "BIPROS/NHAI/RJ/2025/001";
    private static final LocalDate BASELINE_DATE = LocalDate.of(2025, 1, 1);

    private final BaselineRepository baselineRepository;
    private final BaselineActivityRepository baselineActivityRepository;
    private final ActivityRepository activityRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.info("[NH-48 baseline] project '{}' not seeded — skipping baseline", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (baselineRepository.countByProjectId(projectId) > 0) {
            log.info("[NH-48 baseline] baseline already present for {}, skipping", PROJECT_CODE);
            return;
        }

        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.isEmpty()) {
            log.warn("[NH-48 baseline] no activities found for {} — skipping", PROJECT_CODE);
            return;
        }

        // Stable ordering so the variance pattern is reproducible across reboots.
        activities.sort(Comparator.comparing(Activity::getPlannedStartDate,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Activity::getId));

        // Pre-load cost data once so the per-activity BL row carries plannedCost / actualCost.
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
        java.util.List<BaselineActivity> staged = new java.util.ArrayList<>(activities.size());

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
        bl1.setName("BL1 — Initial Baseline");
        bl1.setDescription("Frozen reference plan for " + PROJECT_CODE + ". Used as the source-of-truth " +
                "for schedule + cost variance against the live programme.");
        bl1.setBaselineType(BaselineType.PRIMARY);
        bl1.setBaselineDate(BASELINE_DATE);
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

        // Mark BL1 as the project's active baseline so /reports/variance defaults to it.
        project.setActiveBaselineId(saved.getId());
        projectRepository.save(project);

        log.info("[NH-48 baseline] seeded BL1 for {} with {} activity snapshots (active)",
                PROJECT_CODE, staged.size());
    }

    private static int computeDriftDays(int index) {
        int bucket = index % 3;
        if (bucket == 0) return 0;            // on-track
        if (bucket == 1) return 7 + (index % 15);   // late: 7..21 days slipped
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
