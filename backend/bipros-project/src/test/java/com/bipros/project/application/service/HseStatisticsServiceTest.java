package com.bipros.project.application.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HseStatisticsServiceTest {

    @Mock private DailyProgressReportRepository dprRepository;
    @Mock private DprManpowerRepository manpowerRepository;
    @Mock private DprIssueRepository issueRepository;
    @Mock private ProjectHseMetricsRepository metricsRepository;
    @Mock private HseCalendarResolver calendarResolver;

    private HseStatisticsService service;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HseStatisticsService(
            dprRepository, manpowerRepository, issueRepository, metricsRepository, calendarResolver);
    }

    // ---- helpers ----

    private DailyProgressReport dpr(UUID id, LocalDate date) {
        DailyProgressReport d = new DailyProgressReport();
        d.setId(id);
        d.setReportDate(date);
        return d;
    }

    private DprManpower manpower(UUID dprId, int nos, String workingHours) {
        return DprManpower.builder()
            .dprId(dprId)
            .trade("mason")
            .nos(nos)
            .workingHours(workingHours != null ? new BigDecimal(workingHours) : null)
            .build();
    }

    private void stubApproved(List<DailyProgressReport> dprs) {
        when(dprRepository.findByProjectIdAndApprovalStatusOrderByReportDateAscIdAsc(
            projectId, DprApprovalStatus.APPROVED)).thenReturn(dprs);
    }

    private void stubZeroCounts() {
        when(issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            eq(projectId), any(HseIncidentType.class), eq(IssueStatus.CANCELLED))).thenReturn(0L);
    }

    // ---- man-hours per-row formula ----

    @Test
    void loggedWorkingHours_usedWhenPositive_overCalendar() {
        UUID d1 = UUID.randomUUID();
        stubApproved(List.of(dpr(d1, LocalDate.of(2026, 1, 1))));
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of(manpower(d1, 10, "6")));
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("9"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.manHoursWorked()).isEqualByComparingTo("60");   // 10 * 6 (logged wins)
        assertThat(r.calendarHoursPerDay()).isEqualByComparingTo("9");
    }

    @Test
    void calendarHours_usedWhenWorkingHoursNullOrZero() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        stubApproved(List.of(dpr(d1, LocalDate.of(2026, 1, 1)), dpr(d2, LocalDate.of(2026, 1, 2))));
        when(manpowerRepository.findByDprIdIn(any()))
            .thenReturn(List.of(manpower(d1, 10, null), manpower(d2, 5, "0")));
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("9"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.manHoursWorked()).isEqualByComparingTo("135"); // 10*9 + 5*9
        assertThat(r.projectDaysWorked()).isEqualTo(2);
    }

    @Test
    void eightFallback_flowsThroughFromResolver() {
        UUID d1 = UUID.randomUUID();
        stubApproved(List.of(dpr(d1, LocalDate.of(2026, 1, 1))));
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of(manpower(d1, 5, null)));
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("8"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.manHoursWorked()).isEqualByComparingTo("40");   // 5 * 8
    }

    // ---- without-LTI anchoring ----

    @Test
    void withoutLti_equalsWorked_whenNoLtiLogged() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        stubApproved(List.of(dpr(d1, LocalDate.of(2026, 1, 1)), dpr(d2, LocalDate.of(2026, 1, 2))));
        when(manpowerRepository.findByDprIdIn(any()))
            .thenReturn(List.of(manpower(d1, 10, "6"), manpower(d2, 10, "6")));
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("9"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.manHoursWorked()).isEqualByComparingTo("120");
        assertThat(r.manHoursWithoutLti()).isEqualByComparingTo("120");
        assertThat(r.projectDaysWorked()).isEqualTo(2);
        assertThat(r.projectDaysWithoutLti()).isEqualTo(2);
        assertThat(r.lastLtiDate()).isNull();
    }

    @Test
    void withoutLti_recomputesStrictlyAfterLastLti() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        UUID d3 = UUID.randomUUID();
        LocalDate lti = LocalDate.of(2026, 1, 2);
        stubApproved(List.of(
            dpr(d1, LocalDate.of(2026, 1, 1)),
            dpr(d2, lti),                       // on lastLtiDate -> excluded (strictly after)
            dpr(d3, LocalDate.of(2026, 1, 3)))); // after -> included
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of(
            manpower(d1, 10, "6"),   // 60
            manpower(d2, 10, "6"),   // 60
            manpower(d3, 5, "8")));  // 40
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("9"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.of(
            ProjectHseMetrics.builder().projectId(projectId)
                .kmDistanceDriven(BigDecimal.ZERO).build()));
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.of(lti));
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.manHoursWorked()).isEqualByComparingTo("160");    // derived: 60+60+40
        assertThat(r.manHoursWithoutLti()).isEqualByComparingTo("40"); // only d3
        assertThat(r.projectDaysWorked()).isEqualTo(3);
        assertThat(r.projectDaysWithoutLti()).isEqualTo(1);
        assertThat(r.lastLtiDate()).isEqualTo(lti);
    }

    // ---- incident counts ----

    @Test
    void incidentCounts_perType_excludeCancelledViaStatusNot() {
        stubApproved(List.of());
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("8"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        when(issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.MTC, IssueStatus.CANCELLED)).thenReturn(2L);
        when(issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.PROPERTY_DAMAGE, IssueStatus.CANCELLED)).thenReturn(1L);
        when(issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.NEAR_MISS, IssueStatus.CANCELLED)).thenReturn(3L);
        when(issueRepository.countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.FATALITY, IssueStatus.CANCELLED)).thenReturn(0L);

        var r = service.compute(projectId);

        assertThat(r.mtcCount()).isEqualTo(2);
        assertThat(r.propertyDamageCount()).isEqualTo(1);
        assertThat(r.nearMissCount()).isEqualTo(3);
        assertThat(r.fatalityCount()).isEqualTo(0);
        // documents the CANCELLED-exclusion contract (null-type rows never match = :type)
        verify(issueRepository).countByProjectIdAndHseIncidentTypeAndStatusNot(
            projectId, HseIncidentType.MTC, IssueStatus.CANCELLED);
    }

    // ---- indirect (office) man-hours ----

    @Test
    void indirectManHours_addedToWorkedAndWithoutLti_whenNoLti() {
        UUID d1 = UUID.randomUUID();
        stubApproved(List.of(dpr(d1, LocalDate.of(2026, 1, 1))));
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of(manpower(d1, 10, "6"))); // 60
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("9"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.of(
            ProjectHseMetrics.builder().projectId(projectId)
                .kmDistanceDriven(BigDecimal.ZERO)
                .indirectManHours(new BigDecimal("40")).build()));
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.directManHours()).isEqualByComparingTo("60");
        assertThat(r.indirectManHours()).isEqualByComparingTo("40");
        assertThat(r.manHoursWorked()).isEqualByComparingTo("100");     // 60 direct + 40 indirect
        assertThat(r.manHoursWithoutLti()).isEqualByComparingTo("100"); // no LTI -> equals worked
    }

    @Test
    void indirectManHours_countsInBothWorkedAndWithoutLti_afterLti() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        LocalDate lti = LocalDate.of(2026, 1, 1);
        stubApproved(List.of(
            dpr(d1, lti),                        // on lastLtiDate -> excluded from without-LTI
            dpr(d2, LocalDate.of(2026, 1, 2)))); // after -> included
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of(
            manpower(d1, 10, "6"),   // 60 direct (before/at LTI)
            manpower(d2, 5, "8")));  // 40 direct (after LTI)
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("9"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.of(
            ProjectHseMetrics.builder().projectId(projectId)
                .kmDistanceDriven(BigDecimal.ZERO)
                .indirectManHours(new BigDecimal("1000")).build()));
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.of(lti));
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.directManHours()).isEqualByComparingTo("100");        // 60 + 40
        assertThat(r.manHoursWorked()).isEqualByComparingTo("1100");       // 100 direct + 1000 indirect
        assertThat(r.manHoursWithoutLti()).isEqualByComparingTo("1040");   // 40 direct-after-LTI + 1000 indirect
        assertThat(r.manHoursWorked()).isGreaterThanOrEqualTo(r.manHoursWithoutLti()); // invariant
    }

    @Test
    void indirectManHours_nullOnMetricsRow_treatedAsZero() {
        UUID d1 = UUID.randomUUID();
        stubApproved(List.of(dpr(d1, LocalDate.of(2026, 1, 1))));
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of(manpower(d1, 10, "6"))); // 60
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("9"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.of(
            ProjectHseMetrics.builder().projectId(projectId)
                .kmDistanceDriven(BigDecimal.ZERO).build())); // indirectManHours left null
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.indirectManHours()).isEqualByComparingTo("0");
        assertThat(r.manHoursWorked()).isEqualByComparingTo("60");
    }

    // ---- empty project ----

    @Test
    void emptyProject_allZeros() {
        stubApproved(List.of());
        when(calendarResolver.resolveHoursPerDay(projectId)).thenReturn(new BigDecimal("8"));
        when(metricsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(issueRepository.findLastLtiDate(projectId)).thenReturn(Optional.empty());
        stubZeroCounts();

        var r = service.compute(projectId);

        assertThat(r.manHoursWorked()).isEqualByComparingTo("0");
        assertThat(r.manHoursWithoutLti()).isEqualByComparingTo("0");
        assertThat(r.projectDaysWorked()).isEqualTo(0);
        assertThat(r.projectDaysWithoutLti()).isEqualTo(0);
        assertThat(r.kmDistanceDriven()).isEqualByComparingTo("0");
        assertThat(r.mtcCount()).isEqualTo(0);
        assertThat(r.lastLtiDate()).isNull();
    }
}
