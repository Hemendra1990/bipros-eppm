package com.bipros.scheduling.application.service;

import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.scheduling.application.dto.ScheduleHealthResponse;
import com.bipros.scheduling.domain.model.RiskLevel;
import com.bipros.scheduling.domain.model.ScheduleActivityResult;
import com.bipros.scheduling.domain.model.ScheduleHealthIndex;
import com.bipros.scheduling.domain.model.ScheduleResult;
import com.bipros.scheduling.domain.repository.ScheduleActivityResultRepository;
import com.bipros.scheduling.domain.repository.ScheduleHealthIndexRepository;
import com.bipros.scheduling.domain.repository.ScheduleResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleHealthServiceTest {

    private static final UUID PID = UUID.randomUUID();
    private static final UUID SRID = UUID.randomUUID();

    @Mock ScheduleHealthIndexRepository healthIndexRepo;
    @Mock ScheduleResultRepository scheduleResultRepo;
    @Mock ScheduleActivityResultRepository activityResultRepo;
    @Mock ActivityRelationshipRepository relationshipRepo;
    @Mock ActivityRepository activityRepo;
    @Mock ProjectRepository projectRepo;
    @InjectMocks ScheduleHealthService service;

    private ScheduleActivityResult act(UUID id, double totalFloat, boolean critical) {
        return ScheduleActivityResult.builder()
            .scheduleResultId(SRID).activityId(id).totalFloat(totalFloat).isCritical(critical).build();
    }

    private void stubScheduleResult(LocalDate scheduledFinish) {
        ScheduleResult sr = ScheduleResult.builder()
            .projectId(PID).projectStartDate(LocalDate.of(2026, 3, 19)).projectFinishDate(scheduledFinish).build();
        when(scheduleResultRepo.findById(SRID)).thenReturn(Optional.of(sr));
        when(healthIndexRepo.save(any(ScheduleHealthIndex.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void stubProjectPlanned(LocalDate plannedStart, LocalDate plannedFinish) {
        Project p = new Project();
        p.setPlannedStartDate(plannedStart);
        p.setPlannedFinishDate(plannedFinish);
        when(projectRepo.findById(PID)).thenReturn(Optional.of(p));
    }

    @Test
    void openUnlinkedAndLateScheduleScoresCriticalRisk() {
        // 33 activities, all open (no relationships), big float, scheduled finish 51 days past planned.
        var acts = new java.util.ArrayList<ScheduleActivityResult>();
        for (int i = 0; i < 31; i++) acts.add(act(UUID.randomUUID(), 60.0, false)); // high float (>44)
        acts.add(act(UUID.randomUUID(), 0.0, true));
        acts.add(act(UUID.randomUUID(), 0.0, true));
        when(activityResultRepo.findByScheduleResultId(SRID)).thenReturn(acts);
        when(relationshipRepo.findByProjectId(PID)).thenReturn(List.of()); // 0 relationships → all open
        stubScheduleResult(LocalDate.of(2026, 7, 26));
        stubProjectPlanned(LocalDate.of(2026, 3, 19), LocalDate.of(2026, 6, 5)); // 78-day plan, 51-day slip

        ScheduleHealthResponse r = service.calculateHealth(SRID);

        assertThat(r.missingLogicPct()).isEqualByComparingTo(1.0);   // all open
        assertThat(r.deadlineSlipDays()).isEqualTo(51);
        assertThat(r.healthScore()).isLessThan(40.0);                 // missing-logic 40 + slip ~16 + high-float ~23
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void linkedOnTimeControlledFloatScoresLow() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var acts = List.of(act(a, 10.0, true), act(b, 12.0, false), act(c, 8.0, false));
        when(activityResultRepo.findByScheduleResultId(SRID)).thenReturn(acts);
        // a→b→c: every activity appears as predecessor or successor (none open)
        ActivityRelationship r1 = new ActivityRelationship(); r1.setPredecessorActivityId(a); r1.setSuccessorActivityId(b); r1.setProjectId(PID);
        ActivityRelationship r2 = new ActivityRelationship(); r2.setPredecessorActivityId(b); r2.setSuccessorActivityId(c); r2.setProjectId(PID);
        when(relationshipRepo.findByProjectId(PID)).thenReturn(List.of(r1, r2));
        stubScheduleResult(LocalDate.of(2026, 6, 1)); // on time (before planned finish)
        stubProjectPlanned(LocalDate.of(2026, 3, 19), LocalDate.of(2026, 6, 5));

        ScheduleHealthResponse r = service.calculateHealth(SRID);

        assertThat(r.missingLogicPct()).isEqualByComparingTo(0.0);
        assertThat(r.deadlineSlipDays()).isEqualTo(0);
        assertThat(r.highFloatPct()).isEqualByComparingTo(0.0);      // all floats <= 44
        assertThat(r.healthScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void criticalActivityWithSmallFloatIsNotDoubleCountedAsNearCritical() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var acts = List.of(act(a, 3.0, true), act(b, 3.0, false)); // a: critical AND float 3; b: near-critical
        when(activityResultRepo.findByScheduleResultId(SRID)).thenReturn(acts);
        when(relationshipRepo.findByProjectId(PID)).thenReturn(List.of());
        stubScheduleResult(LocalDate.of(2026, 6, 1));
        stubProjectPlanned(LocalDate.of(2026, 3, 19), LocalDate.of(2026, 6, 5));

        ScheduleHealthResponse r = service.calculateHealth(SRID);

        assertThat(r.criticalActivities()).isEqualTo(1);
        assertThat(r.nearCriticalActivities()).isEqualTo(1); // only b, not a (a is critical)
        int healthy = r.totalActivities() - r.criticalActivities() - r.nearCriticalActivities();
        assertThat(healthy).isEqualTo(0); // partition sums to total, no overlap
    }

    @Test
    void noScheduleRunReturnsNull() {
        when(healthIndexRepo.findTopByProjectIdOrderByCreatedAtDesc(PID)).thenReturn(Optional.empty());
        assertThat(service.getLatestHealth(PID)).isNull();
    }
}
