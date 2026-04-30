package com.bipros.activity.application.scheduling;

import com.bipros.activity.application.percent.PercentCompleteCalculator;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.PercentCompleteType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nightly sweep that recomputes {@code percentComplete} for every in-progress
 * DURATION-typed activity from elapsed days and the project's data date.
 * Uses a multi-instance lease so only one node fires the job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DurationPercentCompleteJob {

    private static final String JOB_NAME = "duration_percent_complete";

    private final ActivityRepository activityRepository;
    private final ProjectRepository projectRepository;
    private final PercentCompleteCalculator calculator;
    private final AuditService auditService;
    private final ScheduledJobLeaseRepository leaseRepository;

    @Scheduled(cron = "${bipros.activity.duration-percent-cron:0 5 2 * * *}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofMinutes(10));
        String owner = "node-" + UUID.randomUUID();
        if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) {
            log.debug("DurationPercentCompleteJob skipped — another node holds the lease");
            return;
        }

        List<Activity> activities = activityRepository.findByPercentCompleteTypeAndStatusIn(
                PercentCompleteType.DURATION, List.of(ActivityStatus.IN_PROGRESS));

        if (activities.isEmpty()) {
            log.debug("No in-progress DURATION activities to process");
            return;
        }

        // Group by project to batch-load data dates
        Map<UUID, LocalDate> dataDateByProject = activities.stream()
                .map(Activity::getProjectId)
                .distinct()
                .collect(Collectors.toMap(
                        pid -> pid,
                        pid -> projectRepository.findById(pid)
                                .map(Project::getDataDate)
                                .orElse(LocalDate.now())));

        int updated = 0;
        for (Activity activity : activities) {
            LocalDate dataDate = dataDateByProject.getOrDefault(activity.getProjectId(), LocalDate.now());
            Double oldPercent = activity.getPercentComplete();

            PercentCompleteCalculator.Result result = calculator.calculate(activity, null, null, dataDate);
            if (result.isKeepPrior()) {
                continue;
            }

            activity.setDurationPercentComplete(result.percent());
            activity.setPercentComplete(result.percent());
            if (result.status() != null) {
                activity.setStatus(result.status());
            }
            if (result.forcedActualFinish() != null) {
                activity.setActualFinishDate(result.forcedActualFinish());
            }

            activityRepository.save(activity);
            if (!java.util.Objects.equals(oldPercent, result.percent())) {
                auditService.logUpdate("Activity", activity.getId(), "percentComplete",
                        oldPercent, result.percent());
            }
            updated++;
        }

        if (updated > 0) {
            log.info("DurationPercentCompleteJob updated {} activities", updated);
        }
    }
}
