package com.bipros.project.application.service;

import com.bipros.project.application.dto.HseStatisticsResponse;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.model.HseIncidentType;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.project.domain.model.ProjectHseMetrics;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.ProjectHseMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only aggregation for the HSE statistics tab. Cumulative, project-to-date. Reads APPROVED
 * DPRs + their manpower rows for man-hours/days, the classified {@link HseIncidentType} counts from
 * DprIssue (excluding CANCELLED), and the manual KM figure from ProjectHseMetrics.
 *
 * <p>Man-hours per manpower row = {@code nos × effectiveHoursPerPerson}, where
 * {@code effectiveHoursPerPerson} is the row's logged {@code workingHours} when {@code > 0}
 * (logged-hours-first), else the project Calendar {@code hoursPerDay} (default 8) resolved once by
 * {@link HseCalendarResolver}. Man-hours worked is the derived direct (DPR) sum PLUS the manual
 * indirect (office) man-hours from ProjectHseMetrics. When an LTI exists, the without-LTI
 * figures are recomputed over APPROVED DPRs strictly after {@code lastLtiDate}; with no
 * LTI they equal the Worked totals.
 */
@Service
@RequiredArgsConstructor
public class HseStatisticsService {

    private final DailyProgressReportRepository dprRepository;
    private final DprManpowerRepository manpowerRepository;
    private final DprIssueRepository issueRepository;
    private final ProjectHseMetricsRepository metricsRepository;
    private final HseCalendarResolver calendarResolver;

    @Transactional(readOnly = true)
    public HseStatisticsResponse compute(UUID projectId) {
        BigDecimal calendarHoursPerDay = calendarResolver.resolveHoursPerDay(projectId);

        List<DailyProgressReport> approved = dprRepository
            .findByProjectIdAndApprovalStatusOrderByReportDateAscIdAsc(
                projectId, DprApprovalStatus.APPROVED);

        Set<UUID> dprIds = approved.stream()
            .map(DailyProgressReport::getId)
            .collect(Collectors.toSet());
        Map<UUID, List<DprManpower>> manpowerByDpr = dprIds.isEmpty()
            ? Map.of()
            : manpowerRepository.findByDprIdIn(dprIds).stream()
                .collect(Collectors.groupingBy(DprManpower::getDprId));

        BigDecimal directManHours = sumManHours(approved, manpowerByDpr, calendarHoursPerDay);
        long projectDaysWorked = distinctDays(approved);

        ProjectHseMetrics metrics = metricsRepository.findByProjectId(projectId).orElse(null);
        BigDecimal kmDistanceDriven = metrics != null && metrics.getKmDistanceDriven() != null
            ? metrics.getKmDistanceDriven() : BigDecimal.ZERO;
        BigDecimal indirectManHours = metrics != null && metrics.getIndirectManHours() != null
            ? metrics.getIndirectManHours() : BigDecimal.ZERO;

        BigDecimal manHoursWorked = directManHours.add(indirectManHours);

        LocalDate lastLtiDate = issueRepository.findLastLtiDate(projectId).orElse(null);

        BigDecimal manHoursWithoutLti;
        long projectDaysWithoutLti;
        if (lastLtiDate == null) {
            // No LTI -> without-LTI equals the Worked total.
            manHoursWithoutLti = manHoursWorked;
            projectDaysWithoutLti = projectDaysWorked;
        } else {
            List<DailyProgressReport> afterLti = approved.stream()
                .filter(d -> d.getReportDate() != null && d.getReportDate().isAfter(lastLtiDate))
                .toList();
            // Indirect (office) man-hours count as safe hours in both totals: office staff aren't
            // exposed to the site injury, and a lump figure has no date to slice by lastLtiDate.
            manHoursWithoutLti =
                sumManHours(afterLti, manpowerByDpr, calendarHoursPerDay).add(indirectManHours);
            projectDaysWithoutLti = distinctDays(afterLti);
        }

        long mtcCount = issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.MTC, IssueStatus.CANCELLED);
        long propertyDamageCount = issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.PROPERTY_DAMAGE, IssueStatus.CANCELLED);
        long nearMissCount = issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.NEAR_MISS, IssueStatus.CANCELLED);
        long fatalityCount = issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.FATALITY, IssueStatus.CANCELLED);

        return new HseStatisticsResponse(
            manHoursWorked,
            manHoursWithoutLti,
            projectDaysWorked,
            projectDaysWithoutLti,
            kmDistanceDriven,
            mtcCount,
            propertyDamageCount,
            nearMissCount,
            fatalityCount,
            lastLtiDate,
            calendarHoursPerDay,
            directManHours,
            indirectManHours);
    }

    /** Σ over the given DPRs' manpower rows of {@code nos × (workingHours>0 ? workingHours : calHours)}. */
    private static BigDecimal sumManHours(
            List<DailyProgressReport> dprs,
            Map<UUID, List<DprManpower>> manpowerByDpr,
            BigDecimal calendarHoursPerDay) {
        BigDecimal total = BigDecimal.ZERO;
        for (DailyProgressReport d : dprs) {
            for (DprManpower m : manpowerByDpr.getOrDefault(d.getId(), List.of())) {
                int nos = m.getNos() != null ? m.getNos() : 0;
                if (nos == 0) continue;
                BigDecimal perPerson =
                    (m.getWorkingHours() != null && m.getWorkingHours().signum() > 0)
                        ? m.getWorkingHours()
                        : calendarHoursPerDay;
                total = total.add(perPerson.multiply(BigDecimal.valueOf(nos)));
            }
        }
        return total;
    }

    private static long distinctDays(List<DailyProgressReport> dprs) {
        return dprs.stream()
            .map(DailyProgressReport::getReportDate)
            .filter(Objects::nonNull)
            .distinct()
            .count();
    }
}
