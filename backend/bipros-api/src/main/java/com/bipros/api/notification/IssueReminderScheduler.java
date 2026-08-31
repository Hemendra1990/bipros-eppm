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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Daily overdue-issue check (Act-by SLA). Day-anchored like {@link DprMissingAlertScheduler}:
 * fires once the local time (in {@code dpr_report_timezone}) has passed
 * {@code issue_reminder_time} (default 09:00). No per-run row is needed — idempotence lives on
 * each issue ({@code last_reminder_at} caps the reminder cadence, {@code escalated_at} makes
 * the manager escalation one-shot), so later ticks the same day are no-ops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssueReminderScheduler {
    private static final String JOB_NAME = "issue_reminder";

    private final DprAlertConfig alertConfig;
    private final DprReportConfig reportConfig;
    private final ProjectRepository projectRepository;
    private final ScheduledJobLeaseRepository leaseRepository;
    private final IssueReminderService reminderService;

    static boolean isDue(ZonedDateTime nowZ, LocalTime reminderTime) {
        return !nowZ.toLocalTime().isBefore(reminderTime);
    }

    @Scheduled(fixedDelayString = "${bipros.dpr.report.tick-ms:900000}")
    public void tick() {
        if (!alertConfig.issueReminderEnabled()) return;
        Instant now = Instant.now();
        ZonedDateTime nowZ = now.atZone(reportConfig.zone());
        if (!isDue(nowZ, alertConfig.issueReminderTime())) return;
        if (leaseRepository.tryAcquire(JOB_NAME, now.plus(Duration.ofMinutes(10)), now, "node-" + UUID.randomUUID()) == 0) return;

        LocalDate today = nowZ.toLocalDate();
        for (Project p : projectRepository.findAllByArchivedAtIsNull()) {
            if (p.getStatus() != ProjectStatus.ACTIVE) continue;
            try {
                reminderService.runForProject(p.getId(), today);
            } catch (Exception ex) {
                log.warn("[IssueReminderScheduler] project={} failed: {}", p.getId(), ex.getMessage(), ex);
            }
        }
    }
}
