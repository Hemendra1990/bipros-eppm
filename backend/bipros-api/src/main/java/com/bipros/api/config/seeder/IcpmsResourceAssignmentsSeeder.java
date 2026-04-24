package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Seeds {@link ResourceAssignment} rows across every project that already has activities so the
 * dashboards, EVM and cost views have a non-empty dataset to render (BUG-008). Runs after the
 * resource, activity and rate seeders have all executed.
 *
 * <p>Strategy: for each project, take up to 10 leaf activities in sort order and assign two
 * pooled resources round-robin (one labour/manpower-ish, one equipment/material). plannedUnits
 * is derived from the activity's duration so plannedCost multiplies out to something realistic
 * once the rate lookup kicks in (see {@link com.bipros.resource.application.service.ResourceAssignmentService#computePlannedCost}).
 */
@Slf4j
@Component
@Profile("dev")
@Order(950)
@RequiredArgsConstructor
public class IcpmsResourceAssignmentsSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final ActivityRepository activityRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceRateRepository resourceRateRepository;
    private final ResourceAssignmentRepository assignmentRepository;

    private static final int MAX_ACTIVITIES_PER_PROJECT = 10;
    private static final double HOURS_PER_WORKING_DAY = 8.0;

    @Override
    @Transactional
    public void run(String... args) {
        if (assignmentRepository.count() > 0) {
            log.info("[IC-PMS Assignments] resource assignments already seeded, skipping");
            return;
        }

        List<Resource> pool = resourceRepository.findAll().stream()
            .filter(r -> r.getResourceType() != null)
            .sorted(Comparator.comparing(Resource::getCode, Comparator.nullsLast(String::compareTo)))
            .toList();
        if (pool.isEmpty()) {
            log.warn("[IC-PMS Assignments] no resources seeded — skipping assignments");
            return;
        }

        int totalCreated = 0;
        for (Project project : projectRepository.findAll()) {
            totalCreated += seedForProject(project, pool);
        }
        log.info("[IC-PMS Assignments] seeded {} resource assignments across {} projects",
            totalCreated, projectRepository.count());
    }

    private int seedForProject(Project project, List<Resource> pool) {
        List<Activity> activities = activityRepository.findByProjectId(project.getId()).stream()
            .filter(a -> a.getPlannedStartDate() != null && a.getPlannedFinishDate() != null)
            .sorted(Comparator.comparing(Activity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Activity::getCode, Comparator.nullsLast(String::compareTo)))
            .limit(MAX_ACTIVITIES_PER_PROJECT)
            .toList();
        if (activities.isEmpty()) return 0;

        int created = 0;
        int resourceIndex = 0;
        for (Activity activity : activities) {
            // Two assignments per activity — different resources to keep the pool rolling.
            for (int slot = 0; slot < 2 && resourceIndex < pool.size() * 2; slot++) {
                Resource resource = pool.get(resourceIndex % pool.size());
                resourceIndex++;
                if (persistAssignment(project, activity, resource)) {
                    created++;
                }
            }
        }
        return created;
    }

    private boolean persistAssignment(Project project, Activity activity, Resource resource) {
        if (!assignmentRepository
            .findByActivityId(activity.getId())
            .stream()
            .filter(a -> a.getResourceId().equals(resource.getId()))
            .findFirst()
            .isEmpty()) {
            return false; // already linked
        }

        double durationDays = activity.getOriginalDuration() != null
            ? activity.getOriginalDuration() : 5.0;
        double plannedUnits = durationDays * HOURS_PER_WORKING_DAY;

        ResourceAssignment assignment = ResourceAssignment.builder()
            .projectId(project.getId())
            .activityId(activity.getId())
            .resourceId(resource.getId())
            .plannedUnits(plannedUnits)
            .remainingUnits(plannedUnits)
            .rateType("STANDARD")
            .plannedStartDate(activity.getPlannedStartDate())
            .plannedFinishDate(activity.getPlannedFinishDate())
            .plannedCost(computeCost(resource.getId(), plannedUnits))
            .build();
        assignmentRepository.save(assignment);
        return true;
    }

    private BigDecimal computeCost(java.util.UUID resourceId, double plannedUnits) {
        List<ResourceRate> rates = resourceRateRepository
            .findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, "STANDARD");
        if (!rates.isEmpty() && rates.get(0).getPricePerUnit() != null) {
            return rates.get(0).getPricePerUnit()
                .multiply(BigDecimal.valueOf(plannedUnits))
                .setScale(2, RoundingMode.HALF_UP);
        }
        // Fallback: use the resource's hourlyRate field.
        return resourceRepository.findById(resourceId)
            .map(Resource::getHourlyRate)
            .filter(h -> h != null && h > 0)
            .map(h -> BigDecimal.valueOf(h * plannedUnits).setScale(2, RoundingMode.HALF_UP))
            .orElse(null);
    }

}
