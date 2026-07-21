package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisorPerformanceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
    private static final LocalDate FUTURE = LocalDate.of(2030, 1, 1);
    private static final LocalDate PAST = LocalDate.of(2020, 1, 1);

    @Mock
    private DailyProgressReportRepository dprRepository;
    @Mock
    private ActivityRepository activityRepository;

    private SupervisorPerformanceAgent agent() {
        // Optional.empty() = no reporting module wired in; the progress comparison must still stand
        // on its own, with efficiency simply absent.
        return new SupervisorPerformanceAgent(dprRepository, activityRepository,
                java.util.Optional.empty(), new ObjectMapper());
    }

    private static Activity activity(double pct, LocalDate plannedFinish) {
        Activity a = new Activity();
        a.setId(UUID.randomUUID());
        a.setProjectId(PROJECT);
        a.setPercentComplete(pct);
        a.setPlannedFinishDate(plannedFinish);
        a.setActualFinishDate(null);
        return a;
    }

    private static DailyProgressReport dpr(UUID userId, String name, LocalDate date, UUID activityId) {
        return DailyProgressReport.builder()
                .projectId(PROJECT)
                .supervisorUserId(userId)
                .supervisorName(name)
                .reportDate(date)
                .activityId(activityId)
                .qtyExecuted(BigDecimal.TEN)
                .approvalStatus(DprApprovalStatus.APPROVED)
                .build();
    }

    /** Files `days` DPRs (distinct dates) cycling across the supervisor's activities. */
    private static void fileReports(List<DailyProgressReport> out, UUID userId, String name,
                                    List<Activity> acts, int days) {
        for (int i = 0; i < days; i++) {
            Activity a = acts.get(i % acts.size());
            out.add(dpr(userId, name, LocalDate.of(2026, 6, 1).plusDays(i), a.getId()));
        }
    }

    @Test
    void comparesSupervisorsAndFlagsLaggard() {
        UUID ua = UUID.randomUUID(), ub = UUID.randomUUID();
        // Ahmed: 4 activities ~85% complete, none late.
        List<Activity> ahmed = List.of(activity(85, FUTURE), activity(88, FUTURE),
                activity(82, FUTURE), activity(85, FUTURE));
        // Bilal: 4 activities ~35% complete, 3 of them overdue.
        List<Activity> bilal = List.of(activity(35, PAST), activity(30, PAST),
                activity(40, PAST), activity(35, FUTURE));

        List<Activity> all = new ArrayList<>();
        all.addAll(ahmed);
        all.addAll(bilal);
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(all);

        List<DailyProgressReport> dprs = new ArrayList<>();
        fileReports(dprs, ua, "Ahmed", ahmed, 6);
        fileReports(dprs, ub, "Bilal", bilal, 6);
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .contains("SUPERVISOR_COMPARISON", "SUPERVISOR_UNDERPERFORMANCE");
        // Bilal (35% vs median 60%, 3/4 late) is HIGH and sorts first; only the laggard is flagged.
        AgentFindingDraft top = c.get(0);
        assertThat(top.findingType()).isEqualTo("SUPERVISOR_UNDERPERFORMANCE");
        assertThat(top.severity()).isEqualTo(Severity.HIGH);
        assertThat(top.title()).contains("Bilal");
        assertThat(c).filteredOn(f -> f.findingType().equals("SUPERVISOR_UNDERPERFORMANCE")).hasSize(1);
        assertThat(result.dataSnapshot().get("supervisorCount").asInt()).isEqualTo(2);
    }

    @Test
    void singleSupervisorYieldsNoComparison() {
        List<Activity> acts = List.of(activity(50, FUTURE), activity(50, FUTURE), activity(50, FUTURE));
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(acts);
        List<DailyProgressReport> dprs = new ArrayList<>();
        fileReports(dprs, UUID.randomUUID(), "Solo", acts, 6);
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);

        assertThat(agent().gather(AgentRunContext.manual(PROJECT, null)).candidates()).isEmpty();
    }

    @Test
    void thinlyLoadedSupervisorsAreExcluded() {
        // Each supervisor has activities but fewer than MIN_ACTIVE_DAYS (5) reporting days.
        List<Activity> a1 = List.of(activity(90, FUTURE), activity(90, FUTURE), activity(90, FUTURE));
        List<Activity> a2 = List.of(activity(20, PAST), activity(20, PAST), activity(20, PAST));
        List<Activity> all = new ArrayList<>();
        all.addAll(a1);
        all.addAll(a2);
        lenient().when(activityRepository.findByProjectId(PROJECT)).thenReturn(all);
        List<DailyProgressReport> dprs = new ArrayList<>();
        fileReports(dprs, UUID.randomUUID(), "Ahmed", a1, 3);
        fileReports(dprs, UUID.randomUUID(), "Bilal", a2, 3);
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("supervisorCount").asInt()).isEqualTo(0);
    }
}
