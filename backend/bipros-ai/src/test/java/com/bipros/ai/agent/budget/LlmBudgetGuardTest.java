package com.bipros.ai.agent.budget;

import com.bipros.ai.agent.domain.AgentBudgetUsage;
import com.bipros.ai.agent.domain.AgentBudgetUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmBudgetGuardTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock
    private AgentBudgetUsageRepository repo;

    private LlmBudgetGuard guard;
    private AgentBudgetProperties props;

    @BeforeEach
    void setUp() {
        props = new AgentBudgetProperties();  // perRun=8000, project=150000, global=2000000
        guard = new LlmBudgetGuard(repo, props);
        lenient().when(repo.save(any(AgentBudgetUsage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AgentBudgetUsage row(UUID projectId, long reserved, long used) {
        AgentBudgetUsage r = new AgentBudgetUsage();
        r.setProjectId(projectId);
        r.setUsageDate(LocalDate.now());
        r.setTokensReserved(reserved);
        r.setTokensUsed(used);
        return r;
    }

    @Test
    void reservesWhenUnderBothCaps() {
        AgentBudgetUsage global = row(AgentBudgetUsage.GLOBAL_SCOPE, 0, 0);
        AgentBudgetUsage project = row(PROJECT, 0, 0);
        when(repo.lockByProjectIdAndUsageDate(eq(AgentBudgetUsage.GLOBAL_SCOPE), any())).thenReturn(Optional.of(global));
        when(repo.lockByProjectIdAndUsageDate(eq(PROJECT), any())).thenReturn(Optional.of(project));

        assertThat(guard.tryReserve(PROJECT)).isTrue();
        assertThat(global.getTokensReserved()).isEqualTo(8000);
        assertThat(project.getTokensReserved()).isEqualTo(8000);
    }

    @Test
    void deniesWhenProjectCapExceeded() {
        AgentBudgetUsage global = row(AgentBudgetUsage.GLOBAL_SCOPE, 0, 0);
        AgentBudgetUsage project = row(PROJECT, 0, 145_000);  // 145000 + 8000 > 150000
        when(repo.lockByProjectIdAndUsageDate(eq(AgentBudgetUsage.GLOBAL_SCOPE), any())).thenReturn(Optional.of(global));
        when(repo.lockByProjectIdAndUsageDate(eq(PROJECT), any())).thenReturn(Optional.of(project));

        assertThat(guard.tryReserve(PROJECT)).isFalse();
        assertThat(project.getTokensReserved()).isZero();
        verify(repo, never()).save(any());
    }

    @Test
    void deniesWhenGlobalCapExceeded() {
        AgentBudgetUsage global = row(AgentBudgetUsage.GLOBAL_SCOPE, 0, 1_999_000);  // +8000 > 2_000_000
        when(repo.lockByProjectIdAndUsageDate(eq(AgentBudgetUsage.GLOBAL_SCOPE), any())).thenReturn(Optional.of(global));
        // project row never consulted because global check fails first
        lenient().when(repo.lockByProjectIdAndUsageDate(eq(PROJECT), any())).thenReturn(Optional.of(row(PROJECT, 0, 0)));

        assertThat(guard.tryReserve(PROJECT)).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    void recordReleasesReservationAndPostsActuals() {
        AgentBudgetUsage global = row(AgentBudgetUsage.GLOBAL_SCOPE, 8000, 0);
        AgentBudgetUsage project = row(PROJECT, 8000, 0);
        when(repo.lockByProjectIdAndUsageDate(eq(AgentBudgetUsage.GLOBAL_SCOPE), any())).thenReturn(Optional.of(global));
        when(repo.lockByProjectIdAndUsageDate(eq(PROJECT), any())).thenReturn(Optional.of(project));

        guard.record(PROJECT, 5200);

        assertThat(global.getTokensReserved()).isZero();
        assertThat(global.getTokensUsed()).isEqualTo(5200);
        assertThat(global.getRunCount()).isEqualTo(1);
        assertThat(project.getTokensReserved()).isZero();
        assertThat(project.getTokensUsed()).isEqualTo(5200);
    }
}
