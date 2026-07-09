package com.bipros.ai.agent.notify;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notification routing + email configuration, bound from {@code bipros.agent.notify} in
 * application.yml.
 *
 * <p>{@link #routing} maps a lower-cased severity name ({@code critical|high|medium|low|info}) to
 * the channels to fan out to and whether delivery is immediate (immediate=false defers to the daily
 * digest). Per-project / global {@code AgentNotificationRule} rows override this yml default.
 */
@Component
@ConfigurationProperties(prefix = "bipros.agent.notify")
@Getter
@Setter
public class AgentNotifyProperties {

    /** severity name (lower-case) -> routing rule. */
    private Map<String, Routing> routing = new LinkedHashMap<>();

    private Email email = new Email();

    @Getter
    @Setter
    public static class Routing {
        /** Comma-separated channel keys, e.g. {@code "in_app,email,whatsapp"}. */
        private String channels = "";
        /** false => defer to the daily digest instead of sending now. */
        private boolean immediate = true;
    }

    @Getter
    @Setter
    public static class Email {
        /** SMTP host gate — blank disables the email channel. */
        private String host = "";
        /** From address on agent emails. */
        private String from = "no-reply@bipros.local";
        /** Frontend base URL prepended to a finding's relative deep link in emails. */
        private String appBaseUrl = "http://localhost:3000";
    }
}
