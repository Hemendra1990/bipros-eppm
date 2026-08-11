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
 * One row per (project, checked date) for the daily missing-DPR alert — the idempotence guard
 * that stops the 15-minute tick from re-alerting the same day. A row is written even when
 * nothing was missing (missingCount 0) or the day was skipped as non-working (emailsSent 0),
 * so each day is evaluated exactly once.
 */
@Entity
@Table(name = "dpr_missing_alert_run", schema = "ai",
       uniqueConstraints = @UniqueConstraint(name = "uq_dpr_missing_alert_run", columnNames = {"project_id", "alert_date"}))
@Getter @Setter @NoArgsConstructor
public class DprMissingAlertRun extends BaseEntity {
    @Column(name = "project_id", nullable = false) private UUID projectId;
    /** The day whose submissions were checked (yesterday in the configured timezone). */
    @Column(name = "alert_date", nullable = false) private LocalDate alertDate;
    @Column(name = "missing_count", nullable = false) private int missingCount;
    @Column(name = "emails_sent", nullable = false) private int emailsSent;
    /** True when the day was a non-working day per the project calendar (no check performed). */
    @Column(name = "skipped_non_working", nullable = false) private boolean skippedNonWorking;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
}
