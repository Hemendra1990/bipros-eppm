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
 * One row per (project, ISO week) for the weekly material short-supply digest — the idempotence
 * guard mirroring {@link IssueDigestRun}. A row is written even when nothing was short (count 0,
 * no emails) so each week is evaluated exactly once.
 */
@Entity
@Table(name = "material_shortage_run", schema = "ai",
       uniqueConstraints = @UniqueConstraint(name = "uq_material_shortage_run", columnNames = {"project_id", "week_start"}))
@Getter @Setter @NoArgsConstructor
public class MaterialShortageRun extends BaseEntity {
    @Column(name = "project_id", nullable = false) private UUID projectId;
    /** Monday of the ISO week this digest covers. */
    @Column(name = "week_start", nullable = false) private LocalDate weekStart;
    @Column(name = "shortage_count", nullable = false) private int shortageCount;
    @Column(name = "emails_sent", nullable = false) private int emailsSent;
    /** False when the project had no store data at all (nothing to evaluate). */
    @Column(name = "tracked", nullable = false) private boolean tracked;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
}
