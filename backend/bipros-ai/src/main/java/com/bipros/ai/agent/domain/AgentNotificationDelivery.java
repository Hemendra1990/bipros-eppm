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

/** Audit row for one notification send of one finding on one channel to one recipient. Idempotent replay guard. */
@Entity
@Table(schema = "ai", name = "agent_notification_delivery", indexes = {
        @Index(name = "idx_agent_delivery_finding", columnList = "finding_id"),
        @Index(name = "idx_agent_delivery_recipient", columnList = "recipient_user_id")
})
@Getter
@Setter
public class AgentNotificationDelivery extends BaseEntity {

    @Column(name = "finding_id", nullable = false)
    private UUID findingId;

    @Column(name = "channel_key", nullable = false, length = 20)
    private String channelKey;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(length = 500)
    private String detail;

    @Column(name = "sent_at")
    private Instant sentAt;
}
