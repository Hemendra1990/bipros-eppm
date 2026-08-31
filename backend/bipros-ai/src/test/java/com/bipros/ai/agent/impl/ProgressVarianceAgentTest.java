package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressVarianceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    @Mock private EvmCalculationRepository evmRepository;
    @Mock private ActivityRepository activityRepository;

    private ProgressVarianceAgent agent() {
        return new ProgressVarianceAgent(evmRepository, activityRepository, new ObjectMapper());
    }

    private static AgentRunContext ctx() {
        return new AgentRunContext(PROJECT, false, "MANUAL", null, true, null, null, NOW);
    }

    private static EvmCalculation projectEvm(double pv, double ev, double bac, Double spi) {
        EvmCalculation e = new EvmCalculation();
        e.setPlannedValue(BigDecimal.valueOf(pv));
        e.setEarnedValue(BigDecimal.valueOf(ev));
        e.setBudgetAtCompletion(BigDecimal.valueOf(bac));
        e.setScheduleVariance(BigDecimal.valueOf(ev - pv));
        e.setSchedulePerformanceIndex(spi);
        e.setDataDate(LocalDate.of(2026, 6, 30));
        return e;
    }

    private static EvmCalculation activityEvm(UUID activityId, double pv, double ev, Double spi) {
        EvmCalculation e = new EvmCalculation();
        e.setActivityId(activityId);
        e.setPlannedValue(BigDecimal.valueOf(pv));
        e.setEarnedValue(BigDecimal.valueOf(ev));
        e.setSchedulePerformanceIndex(spi);
        e.setDataDate(LocalDate.of(2026, 6, 30));
        return e;
    }

    private static Activity milestone(String name, LocalDate due, boolean complete) {
        Activity a = new Activity();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setActivityType(ActivityType.FINISH_MILESTONE);
        a.setPlannedFinishDate(due);
        a.setPercentComplete(complete ? 100.0 : 0.0);
        if (complete) a.setActualFinishDate(due);
        return a;
    }

    @Test
    void flagsBehindScheduleAndOverdueMilestones() {
        when(evmRepository.findTopByProjectIdOrderByDataDateDesc(PROJECT))
                .thenReturn(Optional.of(projectEvm(35_000_000, 12_572_200, 50_000_000, 0.3592)));
        when(evmRepository.findByProjectIdOrderByDataDateDesc(PROJECT)).thenReturn(List.of()); // no per-activity plan
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(List.of(
                milestone("Bridge handover", LocalDate.of(2026, 5, 23), false),  // 39d overdue
                milestone("Section 1 done", LocalDate.of(2026, 6, 25), false),   // 6d overdue
                milestone("Mobilisation", LocalDate.of(2026, 5, 1), true)));     // complete

        GatherResult result = agent().gather(ctx());
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .contains("SCHEDULE_PROGRESS_VARIANCE", "MILESTONE_AT_RISK")
                .doesNotContain("ACTIVITY_PROGRESS_VARIANCE"); // dormant — no per-activity PV
        AgentFindingDraft sv = c.stream()
                .filter(f -> f.findingType().equals("SCHEDULE_PROGRESS_VARIANCE")).findFirst().orElseThrow();
        assertThat(sv.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.dataSnapshot().get("spi").asDouble()).isEqualTo(0.36);
        assertThat(result.dataSnapshot().get("milestonesAtRisk").asInt()).isEqualTo(2);
        assertThat(result.dataSnapshot().get("activitiesScheduled").asInt()).isZero();
    }

    @Test
    void categorisesPerActivityWhenPlannedValueExists() {
        when(evmRepository.findTopByProjectIdOrderByDataDateDesc(PROJECT))
                .thenReturn(Optional.of(projectEvm(35_000_000, 20_000_000, 50_000_000, 0.57)));
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(List.of());
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID(), a3 = UUID.randomUUID();
        when(evmRepository.findByProjectIdOrderByDataDateDesc(PROJECT)).thenReturn(List.of(
                activityEvm(a1, 100, 40, 0.40),   // delayed
                activityEvm(a2, 100, 100, 1.00),  // on track
                activityEvm(a3, 100, 130, 1.30))); // ahead

        GatherResult result = agent().gather(ctx());

        AgentFindingDraft ab = result.candidates().stream()
                .filter(f -> f.findingType().equals("ACTIVITY_PROGRESS_VARIANCE")).findFirst().orElseThrow();
        assertThat(ab.severity()).isEqualTo(Severity.HIGH); // 1/3 delayed AND worst SPI 0.40 < 0.5
        assertThat(result.dataSnapshot().get("activitiesScheduled").asInt()).isEqualTo(3);
        assertThat(result.dataSnapshot().get("activitiesDelayed").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("activitiesAhead").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("activitiesOnTrack").asInt()).isEqualTo(1);
    }

    @Test
    void perActivityDormantWhenPlannedValueZero() {
        when(evmRepository.findTopByProjectIdOrderByDataDateDesc(PROJECT))
                .thenReturn(Optional.of(projectEvm(35_000_000, 12_000_000, 50_000_000, 0.34)));
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(List.of());
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID();
        when(evmRepository.findByProjectIdOrderByDataDateDesc(PROJECT)).thenReturn(List.of(
                activityEvm(a1, 0, 0, 0.0),   // no time-phased plan
                activityEvm(a2, 0, 500, 0.0)));

        GatherResult result = agent().gather(ctx());

        assertThat(result.candidates()).extracting(AgentFindingDraft::findingType)
                .doesNotContain("ACTIVITY_PROGRESS_VARIANCE");
        assertThat(result.dataSnapshot().get("activitiesScheduled").asInt()).isZero();
    }

    @Test
    void noEvmNoMilestonesYieldsNothing() {
        when(evmRepository.findTopByProjectIdOrderByDataDateDesc(PROJECT)).thenReturn(Optional.empty());
        when(evmRepository.findByProjectIdOrderByDataDateDesc(PROJECT)).thenReturn(List.of());
        lenient().when(activityRepository.findByProjectId(PROJECT)).thenReturn(List.of());
        assertThat(agent().gather(ctx()).candidates()).isEmpty();
    }
}
