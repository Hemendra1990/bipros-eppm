package com.bipros.ai.agent.trigger;

import com.bipros.ai.agent.domain.AgentTriggerQueueItem;
import com.bipros.ai.agent.domain.AgentTriggerQueueItemRepository;
import com.bipros.ai.agent.pipeline.AgentPipelineRunner;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Drains the coalescing queue: rows where {@code now >= min(dueAt, maxDueAt)} are dispatched to
 * {@link AgentPipelineRunner}. Lease-guarded so only one node drains (mirrors
 * {@code DprApprovalSlaEscalationJob}). Each row is deleted before dispatch, so the pipeline's own
 * RUNNING-idempotency (not the queue) governs concurrent triggers; re-arriving events re-queue a
 * fresh row.
 *
 * <p>The method is intentionally not {@code @Transactional}: the repository delete and the pipeline
 * run each open their own short transaction, so a long-running pipeline never holds a DB transaction
 * open across the whole drain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTriggerDrainJob {

    private static final String JOB_NAME = "agent_trigger_drain";

    private final AgentTriggerQueueItemRepository queueRepository;
    private final AgentPipelineRunner pipelineRunner;
    private final ScheduledJobLeaseRepository leaseRepository;

    @Scheduled(fixedDelayString = "${bipros.agent.trigger.drain-fixed-delay-ms:30000}")
    public void run() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofMinutes(5));
        String owner = "node-" + UUID.randomUUID();
        if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) {
            return;   // another node holds the lease
        }

        List<AgentTriggerQueueItem> due =
                queueRepository.findByDueAtLessThanEqualOrMaxDueAtLessThanEqual(now, now);
        if (due.isEmpty()) {
            return;
        }

        int dispatched = 0;
        for (AgentTriggerQueueItem item : due) {
            String pipelineKey = item.getPipelineKey();
            UUID projectId = item.getProjectId();
            try {
                queueRepository.delete(item);   // remove before dispatch; events re-queue if they recur
                pipelineRunner.run(pipelineKey, projectId, item.getTriggerType(), item.getTriggerRef());
                dispatched++;
            } catch (Exception ex) {
                log.warn("AgentTriggerDrainJob: failed to dispatch {} for project {}: {}",
                        pipelineKey, projectId, ex.getMessage(), ex);
            }
        }
        if (dispatched > 0) {
            log.info("AgentTriggerDrainJob dispatched {} coalesced pipeline triggers", dispatched);
        }
    }
}
