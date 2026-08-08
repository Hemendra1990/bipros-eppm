package com.bipros.api.dprreport;

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
 * Daily DPR Report generator: iterates active projects and generates a SCHEDULED report for
 * any that are due, per {@link DprReportConfig}. Mirrors the {@code DprApprovalSlaEscalationJob}
 * lease pattern (one-shot per tick, node-safe).
 *
 * <p>Due logic (2026-08-07): day-anchored at the configured send time in the configured
 * timezone ({@code dpr_report_send_time} / {@code dpr_report_timezone}, default 07:00
 * Asia/Muscat) — due once the local time has passed the send time and no successful SCHEDULED
 * report exists yet for the current period (DAILY = today, WEEKLY = the last 7 local days).
 * Drift-free (the anchor is the calendar day, not the previous send instant), catch-up-correct
 * (a JVM down at send time generates on the first tick after boot — one report, no backfill),
 * and FAILED runs don't count as sent, so a failed morning run retries on later ticks the same
 * day. The 15-min tick ({@code bipros.dpr.report.tick-ms}) bounds send accuracy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DprReportScheduler {
    private static final String JOB_NAME = "dpr_report_generation";

    private final DprReportConfig config;
    private final ProjectRepository projectRepository;
    private final DprAgentReportRepository reportRepository;
    private final ScheduledJobLeaseRepository leaseRepository;
    private final DprReportService reportService;

    static boolean isDue(Instant lastRun, ZonedDateTime nowZ, LocalTime sendTime, DprReportConfig.Cadence cadence) {
        if (nowZ.toLocalTime().isBefore(sendTime)) return false;
        if (lastRun == null) return true;
        LocalDate lastDate = lastRun.atZone(nowZ.getZone()).toLocalDate();
        int minDays = cadence == DprReportConfig.Cadence.WEEKLY ? 7 : 1;
        return !lastDate.isAfter(nowZ.toLocalDate().minusDays(minDays));
    }

    @Scheduled(fixedDelayString = "${bipros.dpr.report.tick-ms:900000}")
    public void tick() {
        if (!config.enabled()) return;
        Instant now = Instant.now();
        if (leaseRepository.tryAcquire(JOB_NAME, now.plus(Duration.ofMinutes(10)), now, "node-" + UUID.randomUUID()) == 0) return;

        ZonedDateTime nowZ = now.atZone(config.zone());
        LocalTime sendTime = config.sendTime();
        DprReportConfig.Cadence cadence = config.cadence();
        LocalDate today = nowZ.toLocalDate();
        for (Project p : projectRepository.findAllByArchivedAtIsNull()) {
            if (p.getStatus() != ProjectStatus.ACTIVE) continue;
            try {
                var last = reportRepository.findTopByProjectIdAndTriggerAndStatusNotOrderByGeneratedAtDesc(
                        p.getId(), "SCHEDULED", "FAILED");
                Instant lastRun = last.map(DprAgentReport::getGeneratedAt).orElse(null);
                if (!isDue(lastRun, nowZ, sendTime, cadence)) continue;

                DprReportWindow window = DprReportWindow.ofPreset(config.window(), today, p.getPlannedStartDate());
                reportService.generate(new ReportRequest(
                        p.getId(), window.from(), window.to(), window.label(),
                        null, null, null,
                        "SCHEDULED", null,
                        config.recipientOverrideEmails(), true));
            } catch (Exception ex) {
                log.warn("[DprReportScheduler] project={} failed: {}", p.getId(), ex.getMessage(), ex);
            }
        }
    }
}
