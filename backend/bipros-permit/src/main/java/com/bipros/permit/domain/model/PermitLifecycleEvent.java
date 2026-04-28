package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permit_lifecycle_event", schema = "permit", indexes = {
        @Index(name = "ix_permit_lifecycle_permit_time", columnList = "permit_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitLifecycleEvent extends BaseEntity {

    @Column(name = "permit_id", nullable = false)
    private UUID permitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private LifecycleEventType eventType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private UUID actorUserId;
}
