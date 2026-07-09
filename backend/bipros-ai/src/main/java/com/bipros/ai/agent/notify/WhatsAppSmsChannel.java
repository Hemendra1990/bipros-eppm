package com.bipros.ai.agent.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers the two messaging {@link NotificationChannel} beans — {@code whatsapp} and {@code sms} —
 * both backed by the same {@link MessagingProviderAdapter}. A channel is enabled only when its
 * provider config is active; otherwise it disables itself (logged once) and every send is a no-op.
 * Neither channel ever throws.
 */
@Configuration
public class WhatsAppSmsChannel {

    public static final String WHATSAPP = "whatsapp";
    public static final String SMS = "sms";

    @Bean
    NotificationChannel whatsAppChannel(MessagingProviderAdapter adapter) {
        return new MessagingChannel(WHATSAPP, adapter);
    }

    @Bean
    NotificationChannel smsChannel(MessagingProviderAdapter adapter) {
        return new MessagingChannel(SMS, adapter);
    }

    /** Shared implementation for both message channels, distinguished by {@code key}. */
    static final class MessagingChannel implements NotificationChannel {

        private static final Logger log = LoggerFactory.getLogger(MessagingChannel.class);

        private final String key;
        private final MessagingProviderAdapter adapter;
        private final AtomicBoolean warnedDisabled = new AtomicBoolean(false);

        MessagingChannel(String key, MessagingProviderAdapter adapter) {
            this.key = key;
            this.adapter = adapter;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public boolean isEnabled() {
            boolean configured = adapter.isConfigured(key);
            if (!configured && warnedDisabled.compareAndSet(false, true)) {
                log.info("Channel '{}' disabled — no active provider config; messages will be skipped.", key);
            }
            return configured;
        }

        @Override
        public void send(ResolvedNotification n) {
            if (!isEnabled()) {
                return;
            }
            if (n.phone() == null || n.phone().isBlank()) {
                log.debug("Channel '{}' skipped for finding {} — recipient {} has no phone number.",
                        key, n.findingId(), n.recipientUserId());
                return;
            }
            // adapter.send never throws and returns success/failure; the router records the outcome.
            adapter.send(key, n.phone(), buildMessage(n));
        }

        private static String buildMessage(ResolvedNotification n) {
            StringBuilder sb = new StringBuilder();
            if (n.severity() != null) {
                sb.append('[').append(n.severity().name()).append("] ");
            }
            sb.append(n.title() == null ? "" : n.title());
            if (n.recommendedAction() != null && !n.recommendedAction().isBlank()) {
                sb.append("\nAction: ").append(n.recommendedAction());
            }
            return sb.toString();
        }
    }
}
