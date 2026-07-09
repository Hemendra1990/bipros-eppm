package com.bipros.ai.agent.domain;

import com.bipros.ai.agent.core.Severity;
import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A finding in the shared agent-memory store. Deduped/superseded by {@code fingerprint}
 * (SHA-256 of agentKey|projectId|findingType|subjectRef). At most one ACTIVE row per fingerprint,
 * guaranteed by a partial unique index (Liquibase changeset 118).
 *
 * <p>{@code evidenceJson} and {@code stakeholdersJson} hold JSON (a {@code List<EvidenceRef>} and a
 * {@code Map<String,List<UUID>>} respectively), (de)serialised in {@code AgentMemoryService}.
 */
@Entity
@Table(schema = "ai", name = "agent_finding", indexes = {
        @Index(name = "idx_agent_finding_project_status_sev", columnList = "project_id, status, severity"),
        @Index(name = "idx_agent_finding_run", columnList = "run_id"),
        @Index(name = "idx_agent_finding_agent", columnList = "agent_key, status")
})
@Getter
@Setter
public class AgentFinding extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "agent_key", nullable = false, length = 80)
    private String agentKey;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "finding_type", nullable = false, length = 60)
    private String findingType;

    @Column(name = "subject_ref", length = 200)
    private String subjectRef;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "confidence_basis", length = 300)
    private String confidenceBasis;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "what_happened", columnDefinition = "text")
    private String whatHappened;

    @Column(name = "why_it_happened", columnDefinition = "text")
    private String whyItHappened;

    @Column(name = "business_impact", columnDefinition = "text")
    private String businessImpact;

    @Column(name = "recommended_action", columnDefinition = "text")
    private String recommendedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", columnDefinition = "jsonb")
    private String evidenceJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stakeholders_json", columnDefinition = "jsonb")
    private String stakeholdersJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FindingStatus status = FindingStatus.ACTIVE;

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "notifiable", nullable = false)
    private boolean notifiable = false;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
