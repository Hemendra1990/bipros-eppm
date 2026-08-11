package com.bipros.api.notification;

import com.bipros.api.dprreport.DprReportConfig;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Weekly outstanding-issues digest. Week-anchored variant of the day-anchored schedulers:
 * due once the configured weekday+time (in {@code dpr_report_timezone}) has passed within the
 * current ISO week and no {@link IssueDigestRun} row exists for (project, weekStart) — so a
 * JVM down on the send day catches up later in the same week, one digest per week, no
 * backfill of past weeks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssueDigestScheduler {
    private static final String JOB_NAME = "issue_digest";

    private final DprAlertConfig alertConfig;
    private final DprReportConfig reportConfig;
    private final ProjectRepository projectRepository;
    private final IssueDigestRunRepository runRepository;
    private final ScheduledJobLeaseRepository leaseRepository;
    private final IssueDigestService digestService;

    static boolean isDue(ZonedDateTime nowZ, DayOfWeek day, LocalTime time) {
        LocalDate today = nowZ.toLocalDate();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate anchor = weekStart.with(TemporalAdjusters.nextOrSame(day));
        return today.isAfter(anchor)
            || (today.equals(anchor) && !nowZ.toLocalTime().isBefore(time));
    }

    @Scheduled(fixedDelayString = "${bipros.dpr.report.tick-ms:900000}")
    public void tick() {
        if (!alertConfig.issueDigestEnabled()) return;
        Instant now = Instant.now();
        ZonedDateTime nowZ = now.atZone(reportConfig.zone());
        if (!isDue(nowZ, alertConfig.issueDigestDay(), alertConfig.issueDigestTime())) return;
        if (leaseRepository.tryAcquire(JOB_NAME, now.plus(Duration.ofMinutes(10)), now, "node-" + UUID.randomUUID()) == 0) return;

        LocalDate weekStart = nowZ.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (Project p : projectRepository.findAllByArchivedAtIsNull()) {
            if (p.getStatus() != ProjectStatus.ACTIVE) continue;
            try {
                if (runRepository.existsByProjectIdAndWeekStart(p.getId(), weekStart)) continue;
                digestService.runForProject(p, weekStart);
            } catch (Exception ex) {
                log.warn("[IssueDigestScheduler] project={} failed: {}", p.getId(), ex.getMessage(), ex);
            }
        }
    }
}
