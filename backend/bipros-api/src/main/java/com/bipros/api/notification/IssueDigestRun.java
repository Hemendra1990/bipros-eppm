package com.bipros.api.notification;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per (project, ISO week) for the weekly outstanding-issues digest — the idempotence
 * guard mirroring {@link DprMissingAlertRun}. A row is written even when nothing was
 * outstanding (counts 0, no emails) so each week is evaluated exactly once.
 */
@Entity
@Table(name = "issue_digest_run", schema = "ai",
       uniqueConstraints = @UniqueConstraint(name = "uq_issue_digest_run", columnNames = {"project_id", "week_start"}))
@Getter @Setter @NoArgsConstructor
public class IssueDigestRun extends BaseEntity {
    @Column(name = "project_id", nullable = false) private UUID projectId;
    /** Monday of the ISO week this digest covers. */
    @Column(name = "week_start", nullable = false) private LocalDate weekStart;
    @Column(name = "outstanding_count", nullable = false) private int outstandingCount;
    @Column(name = "critical_count", nullable = false) private int criticalCount;
    @Column(name = "emails_sent", nullable = false) private int emailsSent;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
}
