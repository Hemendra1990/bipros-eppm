package com.bipros.ai.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A coalesced trigger for a pipeline+project. Event listeners upsert this row (bumping
 * {@code lastSeenAt}/{@code eventCount}/{@code dueAt}); a scheduled drainer under a lease
 * dispatches rows where {@code now >= min(dueAt, maxDueAt)}, so a burst of events collapses into
 * one pipeline run. PK is {@code (pipeline_key, project_id)} so a second event for the same
 * pipeline+project updates the existing row instead of inserting.
 */
@Entity
@Table(schema = "ai", name = "agent_trigger_queue")
@IdClass(AgentTriggerQueueId.class)
@Getter
@Setter
public class AgentTriggerQueueItem {

    @Id
    @Column(name = "pipeline_key", nullable = false, length = 60)
    private String pipelineKey;

    @Id
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "trigger_type", nullable = false, length = 40)
    private String triggerType;

    @Column(name = "trigger_ref", length = 200)
    private String triggerRef;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "event_count", nullable = false)
    private int eventCount = 0;

    /** Debounce boundary: {@code lastSeenAt + quietWindow}. Pushed out each new event. */
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /** Hard cap: {@code firstSeenAt + maxWindow}. Guarantees dispatch even under a sustained burst. */
    @Column(name = "max_due_at", nullable = false)
    private Instant maxDueAt;
}
