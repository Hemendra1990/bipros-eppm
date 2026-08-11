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
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Weekly material short-supply digest — week-anchored exactly like {@link IssueDigestScheduler}:
 * due once the configured weekday+time (in {@code dpr_report_timezone}) has passed within the
 * current ISO week and no {@link MaterialShortageRun} row exists for (project, weekStart).
 * Catch-up within the week, one digest per week, no backfill.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaterialShortageScheduler {
    private static final String JOB_NAME = "material_shortage";

    private final DprAlertConfig alertConfig;
    private final DprReportConfig reportConfig;
    private final ProjectRepository projectRepository;
    private final MaterialShortageRunRepository runRepository;
    private final ScheduledJobLeaseRepository leaseRepository;
    private final MaterialShortageService shortageService;

    @Scheduled(fixedDelayString = "${bipros.dpr.report.tick-ms:900000}")
    public void tick() {
        if (!alertConfig.materialShortageEnabled()) return;
        Instant now = Instant.now();
        ZonedDateTime nowZ = now.atZone(reportConfig.zone());
        if (!IssueDigestScheduler.isDue(nowZ, alertConfig.materialShortageDay(), alertConfig.materialShortageTime())) return;
        if (leaseRepository.tryAcquire(JOB_NAME, now.plus(Duration.ofMinutes(10)), now, "node-" + UUID.randomUUID()) == 0) return;

        LocalDate weekStart = nowZ.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (Project p : projectRepository.findAllByArchivedAtIsNull()) {
            if (p.getStatus() != ProjectStatus.ACTIVE) continue;
            try {
                if (runRepository.existsByProjectIdAndWeekStart(p.getId(), weekStart)) continue;
                shortageService.runForProject(p, weekStart);
            } catch (Exception ex) {
                log.warn("[MaterialShortageScheduler] project={} failed: {}", p.getId(), ex.getMessage(), ex);
            }
        }
    }
}
