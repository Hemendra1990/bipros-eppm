package com.bipros.ai.agent.domain;

/** Delivery outcome of one notification send on one channel — recorded on {@link AgentNotificationDelivery}. */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    /** Suppressed by dedup or a disabled channel — not an error. */
    SKIPPED
}
