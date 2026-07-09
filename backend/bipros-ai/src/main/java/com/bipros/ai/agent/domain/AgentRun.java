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

/** One execution of one agent. Records status, token cost, timing and outcome for observability. */
@Entity
@Table(schema = "ai", name = "agent_run", indexes = {
        @Index(name = "idx_agent_run_project_agent", columnList = "project_id, agent_key"),
        @Index(name = "idx_agent_run_pipeline", columnList = "pipeline_run_id"),
        @Index(name = "idx_agent_run_status", columnList = "status")
})
@Getter
@Setter
public class AgentRun extends BaseEntity {

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "agent_key", nullable = false, length = 80)
    private String agentKey;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AgentRunStatus status = AgentRunStatus.RUNNING;

    @Column(name = "trigger_type", nullable = false, length = 40)
    private String triggerType;

    @Column(name = "trigger_ref", length = 200)
    private String triggerRef;

    @Column(name = "data_hash", length = 64)
    private String dataHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_skip_reason", length = 20)
    private LlmSkipReason llmSkipReason = LlmSkipReason.NONE;

    @Column(name = "tokens_input")
    private Integer tokensInput;

    @Column(name = "tokens_output")
    private Integer tokensOutput;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "findings_count", nullable = false)
    private int findingsCount = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
