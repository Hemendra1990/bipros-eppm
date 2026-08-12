package com.bipros.api.notification;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One open idle-material alert per (project, custodian, material, bucket). Carries the reminder
 * count so the sweep can stop mailing after the configured cap, and {@code resolvedAt} so an
 * item that was consumed, returned or scrapped closes itself on the next evaluation.
 *
 * <p>{@code bucketKey} is the activity id for activity-tagged holdings and the literal
 * {@code POOL} for untagged ones — the same key the engine groups by, so one row tracks exactly
 * one line of the email.
 */
@Entity
@Table(name = "material_idle_alert", schema = "ai",
       uniqueConstraints = @UniqueConstraint(name = "uq_material_idle_alert",
           columnNames = {"project_id", "custodian_user_id", "material_key", "bucket_key"}))
@Getter @Setter @NoArgsConstructor
public class MaterialIdleAlert extends BaseEntity {

    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "custodian_user_id", nullable = false) private UUID custodianUserId;
    @Column(name = "material_key", nullable = false, length = 150) private String materialKey;
    /** Activity id, or {@code POOL} for the untagged person bucket. */
    @Column(name = "bucket_key", nullable = false, length = 40) private String bucketKey;
    @Column(name = "activity_id") private UUID activityId;
    @Column(name = "first_excess", precision = 18, scale = 3) private BigDecimal firstExcess;
    @Column(name = "last_excess", precision = 18, scale = 3) private BigDecimal lastExcess;
    @Column(name = "reminder_count", nullable = false) private int reminderCount;
    @Column(name = "first_sent_at") private Instant firstSentAt;
    @Column(name = "last_sent_at") private Instant lastSentAt;
    /** Set once the excess falls back under tolerance — the alert stops being open. */
    @Column(name = "resolved_at") private Instant resolvedAt;
}
