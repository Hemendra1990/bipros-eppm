package com.bipros.ai.agent.pipeline;

import com.bipros.ai.agent.core.AgentRegistry;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.domain.AgentPipelineRun;
import com.bipros.ai.agent.domain.AgentPipelineRunRepository;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.ai.agent.domain.AgentRunStatus;
import com.bipros.ai.agent.domain.PipelineRunStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a {@link PipelineDefinition} as one {@link AgentPipelineRun}: stages execute sequentially,
 * agents within a stage in parallel on {@code agentTaskExecutor}. A single agent failure never
 * aborts the pipeline — it only bumps {@code failed_count}. The final status is
 * {@code COMPLETED} (no failures), {@code PARTIAL} (some failed, some succeeded) or {@code FAILED}
 * (all failed).
 *
 * <p><b>Idempotency.</b> Before starting, an existing {@code RUNNING} run for the same
 * {@code (pipeline, project)} short-circuits and its id is returned. The partial unique index on
 * {@code (pipeline_key, project_id) WHERE status='RUNNING'} (Liquibase changeset 118) is the
 * concurrent backstop — an INSERT race surfaces as {@link DataIntegrityViolationException}, which we
 * catch and resolve to the already-running run. Portfolio pipelines run with a {@code null}
 * projectId (guarded by the sweep lease upstream).
 */
@Slf4j
@Component
public class AgentPipelineRunner {

    private final AgentRegistry registry;
    private final AgentRunService agentRunService;
    private final AgentPipelineRunRepository pipelineRunRepository;
    private final Executor executor;

    public AgentPipelineRunner(AgentRegistry registry,
                               AgentRunService agentRunService,
                               AgentPipelineRunRepository pipelineRunRepository,
                               @Qualifier("agentTaskExecutor") Executor executor) {
        this.registry = registry;
        this.agentRunService = agentRunService;
        this.pipelineRunRepository = pipelineRunRepository;
        this.executor = executor;
    }

    /**
     * Execute a pipeline. Returns the (new or already-running) pipeline run id, or {@code null} for an
     * unknown pipeline key.
     */
    public UUID run(String pipelineKey, UUID projectId, String triggerType, String triggerRef) {
        PipelineDefinition def = AgentPipelines.byKey(pipelineKey);
        if (def == null) {
            log.warn("AgentPipelineRunner: unknown pipeline key '{}' — nothing to run", pipelineKey);
            return null;
        }

        boolean portfolio = projectId == null;
        String trigger = triggerType != null ? triggerType : "PIPELINE";

        // Idempotency short-circuit (project-scoped runs). Portfolio runs (null projectId) are
        // single-fired under the weekly lease, so the exists() check is skipped for them.
        if (projectId != null && pipelineRunRepository.existsByPipelineKeyAndProjectIdAndStatus(
                pipelineKey, projectId, PipelineRunStatus.RUNNING)) {
            return existingRunId(pipelineKey, projectId);
        }

        // Resolve the runnable agents per stage: only keys present in the registry, and — for a
        // portfolio run — only agents that support portfolio (a project-scoped agent would NPE on
        // a null projectId).
        List<List<String>> runnableStages = new ArrayList<>();
        int agentCount = 0;
        for (Set<String> stage : def.stages()) {
            List<String> runnable = new ArrayList<>(stage.size());
            for (String key : stage) {
                if (!registry.exists(key)) {
                    log.warn("Pipeline {} references unregistered agent '{}' — skipping", pipelineKey, key);
                    continue;
                }
                if (portfolio && !registry.get(key).supportsPortfolio()) {
                    log.warn("Pipeline {} (portfolio) skips non-portfolio agent '{}'", pipelineKey, key);
                    continue;
                }
                runnable.add(key);
            }
            if (!runnable.isEmpty()) {
                runnableStages.add(runnable);
                agentCount += runnable.size();
            }
        }

        Instant now = Instant.now();
        AgentPipelineRun run = new AgentPipelineRun();
        run.setPipelineKey(pipelineKey);
        run.setProjectId(projectId);
        run.setStatus(PipelineRunStatus.RUNNING);
        run.setTriggerType(trigger);
        run.setTriggerRef(triggerRef);
        run.setAgentCount(agentCount);
        run.setStartedAt(now);
        try {
            run = pipelineRunRepository.save(run);
        } catch (DataIntegrityViolationException race) {
            // Concurrent trigger already inserted the RUNNING row (partial unique index). Coalesce.
            log.debug("Pipeline {} project {} already RUNNING (unique index) — coalescing", pipelineKey, projectId);
            return existingRunId(pipelineKey, projectId);
        }

        UUID pipelineRunId = run.getId();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (List<String> stage : runnableStages) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(stage.size());
            for (String agentKey : stage) {
                futures.add(CompletableFuture.runAsync(
                        () -> runAgent(agentKey, projectId, portfolio, trigger, triggerRef, pipelineRunId,
                                succeeded, failed),
                        executor));
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception ex) {
                // Each agent already guards itself; a join failure is defensive only.
                log.warn("Pipeline {} stage join failed: {}", pipelineKey, ex.getMessage());
            }
        }

        run.setSucceededCount(succeeded.get());
        run.setFailedCount(failed.get());
        run.setFinishedAt(Instant.now());
        run.setStatus(finalStatus(succeeded.get(), failed.get()));
        pipelineRunRepository.save(run);
        log.info("Pipeline {} finished project={} status={} agents={} ok={} failed={}",
                pipelineKey, projectId, run.getStatus(), agentCount, succeeded.get(), failed.get());
        return pipelineRunId;
    }

    /** Run one agent; never throws — a failure only increments the failed counter. */
    private void runAgent(String agentKey, UUID projectId, boolean portfolio, String trigger,
                          String triggerRef, UUID pipelineRunId, AtomicInteger succeeded, AtomicInteger failed) {
        try {
            AgentRunContext ctx = AgentRunContext.forPipeline(
                    projectId, portfolio, trigger, triggerRef, pipelineRunId, Instant.now());
            AgentRun ar = agentRunService.runSingle(agentKey, ctx);
            if (ar != null && ar.getStatus() == AgentRunStatus.FAILED) {
                failed.incrementAndGet();
            } else {
                succeeded.incrementAndGet();
            }
        } catch (Exception ex) {
            failed.incrementAndGet();
            log.warn("Pipeline agent '{}' failed for project {}: {}", agentKey, projectId, ex.getMessage());
        }
    }

    private UUID existingRunId(String pipelineKey, UUID projectId) {
        return pipelineRunRepository
                .findFirstByPipelineKeyAndProjectIdAndStatusOrderByStartedAtDesc(
                        pipelineKey, projectId, PipelineRunStatus.RUNNING)
                .map(AgentPipelineRun::getId)
                .orElse(null);
    }

    private static PipelineRunStatus finalStatus(int ok, int failed) {
        if (failed == 0) {
            return PipelineRunStatus.COMPLETED;
        }
        return ok == 0 ? PipelineRunStatus.FAILED : PipelineRunStatus.PARTIAL;
    }
}
