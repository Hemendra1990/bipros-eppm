package com.bipros.ai.agent.core;

import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.ai.agent.domain.AgentRunStatus;
import com.bipros.ai.agent.domain.LlmSkipReason;
import com.bipros.ai.provider.LlmProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Template-method base for all agents. Implements the full run lifecycle around a subclass's
 * deterministic {@link #gather}:
 *
 * <ol>
 *   <li>persist a RUNNING {@link AgentRun}, emit {@code run_started};</li>
 *   <li>{@code gather()} → data snapshot + candidate drafts;</li>
 *   <li>data-hash change detection → {@code SKIPPED_NO_CHANGE} (zero LLM cost) when unchanged;</li>
 *   <li>budget reservation → one {@link AgentNarrator} call, or a templated fallback when the LLM is
 *       unconfigured / over budget / failing (the run never blocks on the LLM);</li>
 *   <li>persist findings via {@link com.bipros.ai.agent.memory.AgentMemoryService} (dedup/supersession);</li>
 *   <li>finalize the run and emit {@code finding} / {@code run_finished} events.</li>
 * </ol>
 */
@Slf4j
public abstract class AbstractAgent implements Agent {

    @Autowired
    protected AgentRuntime runtime;

    /** Run the full lifecycle. Never throws — a failure is recorded as a FAILED {@link AgentRun}. */
    public final AgentRun run(AgentRunContext ctx) {
        Instant startedAt = ctx.now() != null ? ctx.now() : Instant.now();
        long startNanos = System.nanoTime();

        AgentRun run = new AgentRun();
        run.setAgentKey(key());
        run.setProjectId(ctx.projectId());
        run.setPipelineRunId(ctx.pipelineRunId());
        run.setTriggerType(ctx.triggerType());
        run.setTriggerRef(ctx.triggerRef());
        run.setStartedAt(startedAt);
        run.setStatus(AgentRunStatus.RUNNING);
        run = runtime.runRepository().save(run);
        emit(AgentStreamEvent.RUN_STARTED, ctx, run, statusPayload("RUNNING", null));

        try {
            emit(AgentStreamEvent.GATHERING, ctx, run, null);
            GatherResult gathered = gather(ctx);
            JsonNode snapshot = gathered.dataSnapshot();
            String hash = snapshot != null ? runtime.dataHash().computeHash(snapshot) : null;
            run.setDataHash(hash);

            if (!ctx.force() && hash != null && matchesLastSucceeded(ctx, hash)) {
                run.setStatus(AgentRunStatus.SKIPPED_NO_CHANGE);
                run.setLlmSkipReason(LlmSkipReason.NO_CHANGE);
                return finishAndSave(run, startedAt, startNanos, ctx, "SKIPPED_NO_CHANGE", 0);
            }

            List<AgentFindingDraft> candidates = gathered.candidates();
            if (candidates.isEmpty()) {
                run.setStatus(AgentRunStatus.SUCCEEDED);
                run.setLlmSkipReason(LlmSkipReason.NONE);
                return finishAndSave(run, startedAt, startNanos, ctx, "SUCCEEDED", 0);
            }

            List<AgentFindingDraft> narrated = candidates;
            LlmSkipReason skip = LlmSkipReason.NONE;
            int tokensIn = 0;
            int tokensOut = 0;
            String model = null;

            Optional<LlmProviderConfig> config = runtime.narrator().defaultConfig();
            if (config.isEmpty()) {
                skip = LlmSkipReason.NOT_CONFIGURED;
            } else if (!runtime.budget().tryReserve(ctx.projectId())) {
                skip = LlmSkipReason.BUDGET;
            } else {
                emit(AgentStreamEvent.NARRATING, ctx, run, null);
                try {
                    AgentNarrator.NarrationResult nr =
                            runtime.narrator().narrate(displayName(), snapshot, candidates, config.get());
                    narrated = nr.drafts();
                    tokensIn = nr.tokensInput();
                    tokensOut = nr.tokensOutput();
                    model = nr.model();
                    runtime.budget().record(ctx.projectId(), (long) (tokensIn + tokensOut));
                } catch (AgentNarrator.NarrationException ne) {
                    skip = LlmSkipReason.LLM_ERROR;
                    log.warn("Agent {} narration failed, using templated fallback: {}", key(), ne.getMessage());
                    runtime.budget().record(ctx.projectId(), 0);
                }
            }

            List<AgentFinding> persisted =
                    runtime.memory().upsertAll(run.getId(), key(), ctx.projectId(), narrated, startedAt);

            run.setFindingsCount(persisted.size());
            run.setTokensInput(tokensIn > 0 ? tokensIn : null);
            run.setTokensOutput(tokensOut > 0 ? tokensOut : null);
            run.setModel(model);
            run.setLlmSkipReason(skip);
            run.setStatus(AgentRunStatus.SUCCEEDED);

            for (AgentFinding f : persisted) {
                emit(AgentStreamEvent.FINDING, ctx, run, findingPayload(f));
            }
            return finishAndSave(run, startedAt, startNanos, ctx, "SUCCEEDED", persisted.size());

        } catch (Exception e) {
            log.error("Agent {} run failed for project {}", key(), ctx.projectId(), e);
            run.setStatus(AgentRunStatus.FAILED);
            run.setErrorMessage(truncate(e.getMessage()));
            return finishAndSave(run, startedAt, startNanos, ctx, "FAILED", run.getFindingsCount());
        }
    }

    private boolean matchesLastSucceeded(AgentRunContext ctx, String hash) {
        Optional<AgentRun> last = ctx.projectId() == null
                ? runtime.runRepository().findFirstByAgentKeyAndProjectIdIsNullAndStatusOrderByStartedAtDesc(
                        key(), AgentRunStatus.SUCCEEDED)
                : runtime.runRepository().findFirstByAgentKeyAndProjectIdAndStatusOrderByStartedAtDesc(
                        key(), ctx.projectId(), AgentRunStatus.SUCCEEDED);
        return last.isPresent() && hash.equals(last.get().getDataHash());
    }

    private AgentRun finishAndSave(AgentRun run, Instant startedAt, long startNanos, AgentRunContext ctx,
                                   String statusLabel, int findings) {
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        run.setDurationMs(elapsedMs);
        run.setFinishedAt(startedAt.plusMillis(elapsedMs));
        AgentRun saved = runtime.runRepository().save(run);
        emit(AgentStreamEvent.RUN_FINISHED, ctx, saved, statusPayload(statusLabel, findings));
        return saved;
    }

    private void emit(String type, AgentRunContext ctx, AgentRun run, JsonNode payload) {
        try {
            runtime.eventHub().emit(new AgentStreamEvent(
                    type, ctx.projectId(), key(), run.getId(), run.getPipelineRunId(), payload, Instant.now()));
        } catch (Exception ignored) {
            // emitting is best-effort; never let it affect the run
        }
    }

    private ObjectNode statusPayload(String status, Integer findings) {
        ObjectNode n = runtime.objectMapper().createObjectNode();
        n.put("agentKey", key());
        n.put("displayName", displayName());
        n.put("status", status);
        if (findings != null) n.put("findingsCount", findings);
        return n;
    }

    private ObjectNode findingPayload(AgentFinding f) {
        ObjectNode n = runtime.objectMapper().createObjectNode();
        n.put("findingId", f.getId().toString());
        n.put("findingType", f.getFindingType());
        n.put("severity", f.getSeverity().name());
        n.put("title", f.getTitle());
        n.put("notifiable", f.isNotifiable());
        return n;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}
