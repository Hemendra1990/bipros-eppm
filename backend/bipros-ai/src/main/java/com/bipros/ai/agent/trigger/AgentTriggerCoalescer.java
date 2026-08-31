package com.bipros.ai.agent.trigger;

import com.bipros.ai.agent.domain.AgentTriggerQueueItem;
import com.bipros.ai.agent.domain.AgentTriggerQueueItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Upserts the coalescing queue row for a {@code (pipeline, project)} trigger. Called from the
 * AFTER_COMMIT {@link AgentTriggerListener}, so it runs in its own {@code REQUIRES_NEW} transaction
 * (there is no active transaction after commit).
 *
 * <ul>
 *   <li><b>Create:</b> {@code firstSeenAt=now}, {@code eventCount=1}, {@code dueAt=now+quietWindow},
 *       {@code maxDueAt=now+maxWindow}.</li>
 *   <li><b>Update:</b> bump {@code lastSeenAt=now}, {@code eventCount++}, {@code dueAt=now+quietWindow}.
 *       {@code maxDueAt} is <b>never</b> pushed out — it is the hard dispatch cap.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTriggerCoalescer {

    private final AgentTriggerQueueItemRepository queueRepository;
    private final AgentTriggerProperties props;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(String pipelineKey, UUID projectId, String triggerType, String triggerRef, Instant now) {
        if (projectId == null) {
            log.warn("AgentTriggerCoalescer: null projectId for pipeline {} — skipping", pipelineKey);
            return;
        }
        Instant quietDue = now.plusSeconds(props.getQuietWindowSeconds());

        AgentTriggerQueueItem existing =
                queueRepository.findByPipelineKeyAndProjectId(pipelineKey, projectId).orElse(null);
        if (existing != null) {
            existing.setLastSeenAt(now);
            existing.setEventCount(existing.getEventCount() + 1);
            existing.setDueAt(quietDue);   // debounce forward; maxDueAt stays put
            queueRepository.save(existing);
            return;
        }

        AgentTriggerQueueItem item = new AgentTriggerQueueItem();
        item.setPipelineKey(pipelineKey);
        item.setProjectId(projectId);
        item.setTriggerType(triggerType);
        item.setTriggerRef(triggerRef);
        item.setFirstSeenAt(now);
        item.setLastSeenAt(now);
        item.setEventCount(1);
        item.setDueAt(quietDue);
        item.setMaxDueAt(now.plus(Duration.ofMinutes(props.getMaxWindowMinutes())));
        try {
            queueRepository.save(item);
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-insert for the same (pipeline, project) PK; the other event's row wins.
            // The event is effectively coalesced — nothing more to do.
            log.debug("AgentTriggerCoalescer: concurrent insert for {}/{} coalesced", pipelineKey, projectId);
        }
    }
}
