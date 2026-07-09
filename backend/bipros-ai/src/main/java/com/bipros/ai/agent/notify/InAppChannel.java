package com.bipros.ai.agent.notify;

import com.bipros.common.notification.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * In-app channel: persists a {@code UserNotification} via the shared {@link NotificationService}
 * (so it shows in the bell/notification centre) and pushes the same payload to the recipient's live
 * SSE stream. Always enabled; never throws.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    public static final String KEY = "in_app";
    /** Notification {@code type} used for both persistence and the 24h dedup lookup. */
    public static final String NOTIFICATION_TYPE = "AGENT_FINDING";

    private final NotificationService notificationService;
    private final NotificationSseHub sseHub;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(ResolvedNotification n) {
        try {
            UUID notificationId = notificationService.create(
                    n.recipientUserId(),
                    NOTIFICATION_TYPE,
                    n.title(),
                    n.whatHappened(),
                    n.deepLink(),
                    n.projectId(),
                    n.findingId());
            sseHub.publish(n.recipientUserId(), payload(n, notificationId));
        } catch (Exception ex) {
            log.warn("InAppChannel send failed for finding {} user {}: {}",
                    n.findingId(), n.recipientUserId(), ex.getMessage());
        }
    }

    private ObjectNode payload(ResolvedNotification n, UUID notificationId) {
        ObjectNode p = objectMapper.createObjectNode();
        if (notificationId != null) {
            p.put("id", notificationId.toString());
        }
        p.put("type", NOTIFICATION_TYPE);
        if (n.findingId() != null) {
            p.put("findingId", n.findingId().toString());
        }
        if (n.severity() != null) {
            p.put("severity", n.severity().name());
        }
        p.put("title", n.title());
        p.put("body", n.whatHappened());
        p.put("linkUrl", n.deepLink());
        return p;
    }
}
