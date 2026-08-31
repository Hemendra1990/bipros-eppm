package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.AgentRuntime;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.baseline.application.dto.BaselineVarianceResponse;
import com.bipros.baseline.application.service.BaselineService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.scheduling.application.dto.ScheduleHealthResponse;
import com.bipros.scheduling.application.service.ScheduleHealthService;
import com.bipros.scheduling.domain.model.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningIntelligenceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID BASELINE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID FOUNDATION = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID STEELWORK = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID CLADDING = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Mock
    private ScheduleHealthService scheduleHealthService;
    @Mock
    private BaselineService baselineService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AgentMemoryService memoryService;

    private PlanningIntelligenceAgent agent() {
        return new PlanningIntelligenceAgent(
                scheduleHealthService, baselineService, projectRepository, new ObjectMapper());
    }

    /** Health index: 30-day slip (12% of duration), 22% missing logic, 35% critical+near-critical. */
    private static ScheduleHealthResponse health() {
        return new ScheduleHealthResponse(
                UUID.randomUUID(), PROJECT, UUID.randomUUID(),
                100,                        // totalActivities
                20,                         // criticalActivities
                15,                         // nearCriticalActivities
                4.0,                        // totalFloatAverage
                55.0,                       // healthScore
                java.util.Map.of(),         // floatDistribution
                RiskLevel.HIGH,
                0.22,                       // missingLogicPct
                0.10,                       // highFloatPct
                0.12,                       // deadlineSlipRatio
                30,                         // deadlineSlipDays
                LocalDate.of(2026, 6, 30),  // plannedFinish
                LocalDate.of(2026, 7, 30),  // scheduledFinish
                Instant.now(),
                false);
    }

    private static List<BaselineVarianceResponse> variance() {
        return List.of(
                new BaselineVarianceResponse(FOUNDATION, "Foundation", 5L, 20L, 5.0, new BigDecimal("100000"), true),
                new BaselineVarianceResponse(STEELWORK, "Steelwork", 3L, 8L, 3.0, new BigDecimal("50000"), true),
                new BaselineVarianceResponse(CLADDING, "Cladding", 0L, 0L, 0.0, BigDecimal.ZERO, true));
    }

    private static AgentFindingDraft byType(List<AgentFindingDraft> c, String type) {
        return c.stream().filter(f -> f.findingType().equals(type)).findFirst().orElseThrow();
    }

    @Test
    void emitsScheduleHealthAndBaselineDriftFindings() {
        Project project = new Project();
        project.setPrimaryBaselineId(BASELINE);

        when(scheduleHealthService.getLatestHealth(PROJECT)).thenReturn(health());
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        when(baselineService.getVariance(PROJECT, BASELINE)).thenReturn(variance());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .containsExactlyInAnyOrder(
                        "CRITICAL_PATH_SLIP", "LOGIC_QUALITY", "FLOAT_EROSION", "BASELINE_DRIFT");

        // Most-severe first; the three HIGH findings precede the MEDIUM float-erosion finding.
        assertThat(c.get(c.size() - 1).findingType()).isEqualTo("FLOAT_EROSION");
        assertThat(c.get(c.size() - 1).severity()).isEqualTo(Severity.MEDIUM);

        AgentFindingDraft slip = byType(c, "CRITICAL_PATH_SLIP");
        assertThat(slip.severity()).isEqualTo(Severity.HIGH);          // ratio 0.12 → HIGH
        assertThat(slip.subjectRef()).isEqualTo("PROJECT");
        assertThat(slip.confidence()).isBetween(0.0, 1.0);
        assertThat(slip.confidenceBasis()).contains("55");             // health score in the basis
        assertThat(slip.evidence()).anySatisfy(e -> assertThat(e.label()).isEqualTo("Deadline slip"));

        assertThat(byType(c, "LOGIC_QUALITY").severity()).isEqualTo(Severity.HIGH);  // 0.22 → HIGH

        AgentFindingDraft drift = byType(c, "BASELINE_DRIFT");
        assertThat(drift.severity()).isEqualTo(Severity.HIGH);          // maxSlip 20 → HIGH
        assertThat(drift.subjectRef()).isEqualTo("PROJECT");
        assertThat(drift.evidence()).anySatisfy(e ->
                assertThat(e.value()).isEqualTo("Foundation"));         // worst-drifting activity

        // Snapshot carries both the health and baseline sections.
        assertThat(result.dataSnapshot().has("health")).isTrue();
        assertThat(result.dataSnapshot().has("baseline")).isTrue();
        assertThat(result.dataSnapshot().get("baseline").get("drifted").asInt()).isEqualTo(2);
    }

    @Test
    void noHealthAndNoBaselineYieldsNoFindings() {
        Project project = new Project();   // primaryBaselineId stays null

        when(scheduleHealthService.getLatestHealth(PROJECT)).thenReturn(null);
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().size()).isZero();
    }

    @Test
    void referencesOverAllocatedCapacityInBusinessImpact() throws Exception {
        Project project = new Project();
        project.setPrimaryBaselineId(BASELINE);

        AgentFinding overAlloc = new AgentFinding();
        overAlloc.setFindingType("RESOURCE_OVERALLOCATION");
        overAlloc.setTitle("Tower Crane is over-allocated (peak 140%)");
        overAlloc.setSeverity(Severity.HIGH);

        when(scheduleHealthService.getLatestHealth(PROJECT)).thenReturn(health());
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        when(baselineService.getVariance(PROJECT, BASELINE)).thenReturn(variance());
        when(memoryService.activeFindings(eq(PROJECT), anySet(), eq(Severity.MEDIUM)))
                .thenReturn(List.of(overAlloc));

        PlanningIntelligenceAgent agent = agent();
        injectRuntime(agent, new AgentRuntime(null, memoryService, null, null, null, null, new ObjectMapper()));

        GatherResult result = agent.gather(AgentRunContext.manual(PROJECT, null));

        assertThat(byType(result.candidates(), "CRITICAL_PATH_SLIP").businessImpact())
                .contains("Tower Crane is over-allocated");
    }

    private static void injectRuntime(PlanningIntelligenceAgent agent, AgentRuntime runtime) throws Exception {
        Field f = AbstractAgent.class.getDeclaredField("runtime");
        f.setAccessible(true);
        f.set(agent, runtime);
    }
}
