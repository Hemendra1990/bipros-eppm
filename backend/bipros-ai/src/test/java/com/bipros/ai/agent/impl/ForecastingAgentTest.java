package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.AgentRuntime;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.risk.application.service.MonteCarloService;
import com.bipros.risk.domain.model.MonteCarloSimulation;
import com.bipros.risk.domain.repository.MonteCarloSimulationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastingAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SIM = UUID.fromString("00000000-0000-0000-0000-0000000000dd");

    @Mock
    private MonteCarloSimulationRepository monteCarloSimulationRepository;
    @Mock
    private MonteCarloService monteCarloService;
    @Mock
    private com.bipros.evm.application.service.EvmService evmService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AgentMemoryService memoryService;

    private ForecastingAgent agent() {
        return new ForecastingAgent(monteCarloSimulationRepository, monteCarloService,
                evmService, projectRepository, new ObjectMapper());
    }

    /** Completed sim: baseline 200d, P80 224d (12% overrun); baseline cost 5M, P80 cost 5.75M (15% gap). */
    private static MonteCarloSimulation completedSim() {
        MonteCarloSimulation s = new MonteCarloSimulation();
        s.setId(SIM);
        s.setProjectId(PROJECT);
        s.setStatus(MonteCarloSimulation.MonteCarloStatus.COMPLETED);
        s.setIterations(10_000);
        s.setIterationsCompleted(10_000);
        s.setBaselineDuration(200.0);
        s.setConfidenceP50Duration(210.0);
        s.setConfidenceP80Duration(224.0);
        s.setBaselineCost(new BigDecimal("5000000"));
        s.setConfidenceP80Cost(new BigDecimal("5750000"));
        return s;
    }

    /** BAC 10M, EAC 11.2M (12% overrun), CPI 0.85, 40% performance-complete. */
    private static EvmCalculation evm() {
        EvmCalculation e = new EvmCalculation();
        e.setProjectId(PROJECT);
        e.setBudgetAtCompletion(new BigDecimal("10000000"));
        e.setEstimateAtCompletion(new BigDecimal("11200000"));
        e.setVarianceAtCompletion(new BigDecimal("-1200000"));
        e.setCostPerformanceIndex(0.85);
        e.setPerformancePercentComplete(40.0);
        return e;
    }

    private static Project projectWithDates() {
        Project p = new Project();
        p.setPlannedStartDate(LocalDate.of(2026, 1, 1));       // + 224 days -> 2026-08-13
        p.setMustFinishByDate(LocalDate.of(2026, 8, 1));        // P80 finish breaches by ~12 days
        return p;
    }

    private static AgentFindingDraft byType(List<AgentFindingDraft> c, String type) {
        return c.stream().filter(f -> f.findingType().equals(type)).findFirst().orElseThrow();
    }

    @Test
    void emitsCompletionCostAndCashflowFindings() {
        when(monteCarloSimulationRepository.findLatestByProjectId(PROJECT))
                .thenReturn(Optional.of(completedSim()));
        when(evmService.computeEvmSnapshot(PROJECT)).thenReturn(evm());
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(projectWithDates()));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .containsExactlyInAnyOrder("COMPLETION_FORECAST", "COST_AT_COMPLETION", "CASHFLOW_PRESSURE");

        AgentFindingDraft completion = byType(c, "COMPLETION_FORECAST");
        assertThat(completion.severity()).isEqualTo(Severity.HIGH);       // ratio 0.12 -> HIGH
        assertThat(completion.subjectRef()).isEqualTo("PROJECT");
        assertThat(completion.confidence()).isEqualTo(0.80);
        assertThat(completion.confidenceBasis()).contains("P80").contains("10000");
        assertThat(completion.evidence())
                .anySatisfy(e -> assertThat(e.label()).isEqualTo("P80 forecast duration"))
                .anySatisfy(e -> assertThat(e.label()).isEqualTo("Days past committed finish"))
                .anySatisfy(e -> assertThat(e.label()).isEqualTo("P80 completion date"));

        AgentFindingDraft cost = byType(c, "COST_AT_COMPLETION");
        assertThat(cost.severity()).isEqualTo(Severity.HIGH);              // ratio 0.12 -> HIGH
        assertThat(cost.confidence()).isBetween(0.0, 1.0);
        assertThat(cost.confidenceBasis()).contains("P80");               // MC corroboration named
        assertThat(cost.evidence())
                .anySatisfy(e -> assertThat(e.label()).isEqualTo("Estimate at completion (EAC)"));

        AgentFindingDraft cashflow = byType(c, "CASHFLOW_PRESSURE");
        assertThat(cashflow.severity()).isEqualTo(Severity.HIGH);          // gap ratio 0.15 -> HIGH
        assertThat(cashflow.confidence()).isEqualTo(0.80);
        assertThat(cashflow.confidenceBasis()).contains("P80 cost");

        assertThat(result.dataSnapshot().has("monteCarloSchedule")).isTrue();
        assertThat(result.dataSnapshot().has("evm")).isTrue();
        assertThat(result.dataSnapshot().has("monteCarloCost")).isTrue();
    }

    @Test
    void noSimAndNoBudgetYieldsNoFindings() {
        // No persisted sim, fresh run fails (no schedule), EVM has no budget -> degrade to empty.
        when(monteCarloSimulationRepository.findLatestByProjectId(PROJECT)).thenReturn(Optional.empty());
        when(monteCarloService.runSimulation(eq(PROJECT), any()))
                .thenThrow(new RuntimeException("no schedule to simulate"));
        when(evmService.computeEvmSnapshot(PROJECT)).thenReturn(new EvmCalculation());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().size()).isZero();
    }

    @Test
    void referencesRelatedPlanningRiskFindingsInBusinessImpact() throws Exception {
        AgentFinding related = new AgentFinding();
        related.setFindingType("CRITICAL_PATH_SLIP");
        related.setTitle("Critical path slips 30 days past the planned finish");
        related.setSeverity(Severity.HIGH);

        when(monteCarloSimulationRepository.findLatestByProjectId(PROJECT))
                .thenReturn(Optional.of(completedSim()));
        when(evmService.computeEvmSnapshot(PROJECT)).thenReturn(evm());
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(projectWithDates()));
        when(memoryService.activeFindings(eq(PROJECT), anySet(), eq(Severity.MEDIUM)))
                .thenReturn(List.of(related));

        ForecastingAgent agent = agent();
        injectRuntime(agent, new AgentRuntime(null, memoryService, null, null, null, null, new ObjectMapper()));

        GatherResult result = agent.gather(AgentRunContext.manual(PROJECT, null));

        assertThat(byType(result.candidates(), "COMPLETION_FORECAST").businessImpact())
                .contains("Critical path slips 30 days");
    }

    private static void injectRuntime(ForecastingAgent agent, AgentRuntime runtime) throws Exception {
        Field f = AbstractAgent.class.getDeclaredField("runtime");
        f.setAccessible(true);
        f.set(agent, runtime);
    }
}
