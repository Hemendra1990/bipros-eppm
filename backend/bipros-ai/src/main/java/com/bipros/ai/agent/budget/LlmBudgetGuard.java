package com.bipros.ai.agent.budget;

import com.bipros.ai.agent.domain.AgentBudgetUsage;
import com.bipros.ai.agent.domain.AgentBudgetUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Atomic per-project + global daily token budget. {@link #tryReserve} optimistically reserves the
 * per-run cap under a row lock; {@link #record} reconciles the reservation with the actual usage
 * once a run completes. Row-level {@code PESSIMISTIC_WRITE} locks (global row always locked first)
 * make it multi-node safe. A denied reservation is normal — the caller falls back to templated
 * narration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmBudgetGuard {

    private final AgentBudgetUsageRepository repository;
    private final AgentBudgetProperties props;

    /**
     * Try to reserve {@code perRunTokens} against the global budget and (if project-scoped) the
     * project's daily budget.
     *
     * @return true if reserved; false if either cap would be exceeded
     */
    @Transactional
    public boolean tryReserve(UUID projectId) {
        LocalDate today = LocalDate.now();
        long perRun = props.getPerRunTokens();

        // Always lock the global row first to keep a consistent lock order across threads/nodes.
        AgentBudgetUsage global = getOrCreateLocked(AgentBudgetUsage.GLOBAL_SCOPE, today);
        if (global.getTokensReserved() + global.getTokensUsed() + perRun > props.getGlobalDailyTokens()) {
            log.warn("Global daily LLM budget exhausted for {} (cap {})", today, props.getGlobalDailyTokens());
            return false;
        }

        AgentBudgetUsage project = null;
        if (projectId != null) {
            project = getOrCreateLocked(projectId, today);
            if (project.getTokensReserved() + project.getTokensUsed() + perRun > props.getPerProjectDailyTokens()) {
                log.warn("Project {} daily LLM budget exhausted for {} (cap {})",
                        projectId, today, props.getPerProjectDailyTokens());
                return false;
            }
        }

        global.setTokensReserved(global.getTokensReserved() + perRun);
        repository.save(global);
        if (project != null) {
            project.setTokensReserved(project.getTokensReserved() + perRun);
            repository.save(project);
        }
        return true;
    }

    /** Reconcile a prior {@link #tryReserve}: release the reservation and post the actual usage. */
    @Transactional
    public void record(UUID projectId, long tokensUsed) {
        LocalDate today = LocalDate.now();
        long perRun = props.getPerRunTokens();

        AgentBudgetUsage global = getOrCreateLocked(AgentBudgetUsage.GLOBAL_SCOPE, today);
        applyActuals(global, perRun, tokensUsed);
        repository.save(global);

        if (projectId != null) {
            AgentBudgetUsage project = getOrCreateLocked(projectId, today);
            applyActuals(project, perRun, tokensUsed);
            repository.save(project);
        }
    }

    private void applyActuals(AgentBudgetUsage row, long reservedPerRun, long tokensUsed) {
        row.setTokensReserved(Math.max(0, row.getTokensReserved() - reservedPerRun));
        row.setTokensUsed(row.getTokensUsed() + Math.max(0, tokensUsed));
        row.setRunCount(row.getRunCount() + 1);
    }

    private AgentBudgetUsage getOrCreateLocked(UUID projectId, LocalDate date) {
        return repository.lockByProjectIdAndUsageDate(projectId, date)
                .orElseGet(() -> {
                    AgentBudgetUsage row = new AgentBudgetUsage();
                    row.setProjectId(projectId);
                    row.setUsageDate(date);
                    // Insert then re-lock so subsequent reads in this tx see a locked row. The unique
                    // constraint on (project_id, usage_date) prevents duplicate rows under a race.
                    return repository.saveAndFlush(row);
                });
    }
}
