package com.bipros.ai.agent.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One execution of a multi-stage pipeline (a fixed set of agents run in stages). Idempotency for
 * concurrent triggers is enforced by a partial unique index on {@code (pipeline_key, project_id)}
 * WHERE status = 'RUNNING' (see Liquibase changeset 118).
 */
@Entity
@Table(schema = "ai", name = "agent_pipeline_run", indexes = {
        @Index(name = "idx_agent_pipeline_run_project", columnList = "pipeline_key, project_id")
})
@Getter
@Setter
public class AgentPipelineRun extends BaseEntity {

    @Column(name = "pipeline_key", nullable = false, length = 60)
    private String pipelineKey;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PipelineRunStatus status = PipelineRunStatus.RUNNING;

    @Column(name = "trigger_type", nullable = false, length = 40)
    private String triggerType;

    @Column(name = "trigger_ref", length = 200)
    private String triggerRef;

    @Column(name = "agent_count", nullable = false)
    private int agentCount = 0;

    @Column(name = "succeeded_count", nullable = false)
    private int succeededCount = 0;

    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
