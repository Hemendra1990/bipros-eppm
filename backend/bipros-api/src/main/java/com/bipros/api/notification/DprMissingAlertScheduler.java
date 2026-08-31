package com.bipros.api.notification;

import com.bipros.api.dprreport.DprReportConfig;
import com.bipros.calendar.application.service.CalendarService;
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
 * Daily missing-DPR check. Day-anchored like {@link com.bipros.api.dprreport.DprReportScheduler}:
 * fires once the local time (in {@code dpr_report_timezone}) has passed
 * {@code dpr_missing_alert_time} (default 09:00), checking the PREVIOUS day's submissions.
 * The per-(project, date) run row is the idempotence guard, so a JVM restart or downtime
 * catches up on the next tick without double-alerting. Non-working days per the project's
 * default calendar are recorded as skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DprMissingAlertScheduler {
    private static final String JOB_NAME = "dpr_missing_alert";

    private final DprAlertConfig alertConfig;
    private final DprReportConfig reportConfig;
    private final ProjectRepository projectRepository;
    private final DprMissingAlertRunRepository runRepository;
    private final ScheduledJobLeaseRepository leaseRepository;
    private final DprMissingAlertService alertService;
    private final CalendarService calendarService;

    static boolean isDue(ZonedDateTime nowZ, LocalTime alertTime) {
        return !nowZ.toLocalTime().isBefore(alertTime);
    }

    @Scheduled(fixedDelayString = "${bipros.dpr.report.tick-ms:900000}")
    public void tick() {
        if (!alertConfig.missingAlertEnabled()) return;
        Instant now = Instant.now();
        ZonedDateTime nowZ = now.atZone(reportConfig.zone());
        if (!isDue(nowZ, alertConfig.missingAlertTime())) return;
        if (leaseRepository.tryAcquire(JOB_NAME, now.plus(Duration.ofMinutes(10)), now, "node-" + UUID.randomUUID()) == 0) return;

        LocalDate target = nowZ.toLocalDate().minusDays(1);
        for (Project p : projectRepository.findAllByArchivedAtIsNull()) {
            if (p.getStatus() != ProjectStatus.ACTIVE) continue;
            try {
                if (runRepository.existsByProjectIdAndAlertDate(p.getId(), target)) continue;
                if (isNonWorkingDay(p, target)) {
                    alertService.recordSkippedNonWorkingDay(p.getId(), target);
                    continue;
                }
                alertService.runForProject(p, target);
            } catch (Exception ex) {
                log.warn("[DprMissingAlertScheduler] project={} failed: {}", p.getId(), ex.getMessage(), ex);
            }
        }
    }

    /** Defensive: a broken/missing calendar must never block the alert — treat as working. */
    private boolean isNonWorkingDay(Project p, LocalDate date) {
        if (p.getCalendarId() == null) return false;
        try {
            return !calendarService.isWorkingDay(p.getCalendarId(), date);
        } catch (Exception ex) {
            log.debug("[DprMissingAlertScheduler] calendar check failed for project {}: {}", p.getId(), ex.getMessage());
            return false;
        }
    }
}
