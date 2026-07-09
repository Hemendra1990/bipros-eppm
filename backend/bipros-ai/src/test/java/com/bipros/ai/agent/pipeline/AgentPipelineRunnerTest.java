package com.bipros.ai.agent.pipeline;

import com.bipros.ai.agent.core.AgentRegistry;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.domain.AgentPipelineRun;
import com.bipros.ai.agent.domain.AgentPipelineRunRepository;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.ai.agent.domain.AgentRunStatus;
import com.bipros.ai.agent.domain.PipelineRunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPipelineRunnerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    /** Runs submitted tasks synchronously on the calling thread — deterministic for assertions. */
    private static final Executor DIRECT = Runnable::run;

    @Mock
    private AgentRegistry registry;
    @Mock
    private AgentRunService agentRunService;
    @Mock
    private AgentPipelineRunRepository pipelineRunRepository;

    private AgentPipelineRunner runner() {
        return new AgentPipelineRunner(registry, agentRunService, pipelineRunRepository, DIRECT);
    }

    private static AgentRun runWithStatus(AgentRunStatus status) {
        AgentRun run = new AgentRun();
        run.setStatus(status);
        return run;
    }

    @Test
    void allAgentsSucceed_pipelineCompleted() {
        when(registry.exists(anyString())).thenReturn(true);
        when(pipelineRunRepository.existsByPipelineKeyAndProjectIdAndStatus(anyString(), any(), any()))
                .thenReturn(false);
        when(pipelineRunRepository.save(any(AgentPipelineRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agentRunService.runSingle(anyString(), any(AgentRunContext.class)))
                .thenReturn(runWithStatus(AgentRunStatus.SUCCEEDED));

        runner().run(AgentPipelines.OPERATIONS_REACTIVE, PROJECT, "EVENT", "DprSubmittedEvent");

        ArgumentCaptor<AgentPipelineRun> captor = ArgumentCaptor.forClass(AgentPipelineRun.class);
        verify(pipelineRunRepository, times(2)).save(captor.capture());
        AgentPipelineRun finalRun = captor.getValue();
        assertThat(finalRun.getStatus()).isEqualTo(PipelineRunStatus.COMPLETED);
        // OPERATIONS_REACTIVE = {dpr, dbs, capacity} then {notification} = 4 agents.
        assertThat(finalRun.getAgentCount()).isEqualTo(4);
        assertThat(finalRun.getSucceededCount()).isEqualTo(4);
        assertThat(finalRun.getFailedCount()).isZero();
    }

    @Test
    void oneAgentFails_pipelinePartial_notAborted() {
        when(registry.exists(anyString())).thenReturn(true);
        when(pipelineRunRepository.existsByPipelineKeyAndProjectIdAndStatus(anyString(), any(), any()))
                .thenReturn(false);
        when(pipelineRunRepository.save(any(AgentPipelineRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agentRunService.runSingle(anyString(), any(AgentRunContext.class)))
                .thenReturn(runWithStatus(AgentRunStatus.SUCCEEDED));
        when(agentRunService.runSingle(eq("dbs_validation"), any(AgentRunContext.class)))
                .thenReturn(runWithStatus(AgentRunStatus.FAILED));

        runner().run(AgentPipelines.OPERATIONS_REACTIVE, PROJECT, "EVENT", "DprSubmittedEvent");

        ArgumentCaptor<AgentPipelineRun> captor = ArgumentCaptor.forClass(AgentPipelineRun.class);
        verify(pipelineRunRepository, times(2)).save(captor.capture());
        AgentPipelineRun finalRun = captor.getValue();
        assertThat(finalRun.getStatus()).isEqualTo(PipelineRunStatus.PARTIAL);
        // dbs_validation FAILED; dpr + capacity + notification SUCCEEDED.
        assertThat(finalRun.getSucceededCount()).isEqualTo(3);
        assertThat(finalRun.getFailedCount()).isEqualTo(1);
    }

    @Test
    void existingRunningPipeline_shortCircuits_noNewRun() {
        AgentPipelineRun existing = new AgentPipelineRun();
        when(pipelineRunRepository.existsByPipelineKeyAndProjectIdAndStatus(
                eq(AgentPipelines.OPERATIONS_REACTIVE), eq(PROJECT), eq(PipelineRunStatus.RUNNING)))
                .thenReturn(true);
        when(pipelineRunRepository.findFirstByPipelineKeyAndProjectIdAndStatusOrderByStartedAtDesc(
                eq(AgentPipelines.OPERATIONS_REACTIVE), eq(PROJECT), eq(PipelineRunStatus.RUNNING)))
                .thenReturn(Optional.of(existing));

        runner().run(AgentPipelines.OPERATIONS_REACTIVE, PROJECT, "EVENT", "DprSubmittedEvent");

        verify(pipelineRunRepository, never()).save(any());
        verify(agentRunService, never()).runSingle(anyString(), any());
    }

    @Test
    void unknownPipeline_returnsNull_touchesNothing() {
        assertThat(runner().run("NOT_A_PIPELINE", PROJECT, "EVENT", "x")).isNull();
        verify(pipelineRunRepository, never()).save(any());
        verify(agentRunService, never()).runSingle(anyString(), any());
    }
}
