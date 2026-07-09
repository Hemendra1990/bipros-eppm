package com.bipros.ai.agent.notify;

/**
 * One outbound delivery channel (in-app, email, whatsapp, sms). Implementations are discovered as
 * Spring beans and indexed by {@link #key()} in {@link NotificationRouter}.
 *
 * <p>Contract: {@link #send} MUST NOT throw. A transport failure is swallowed and logged (and the
 * router records a delivery outcome) so one broken channel never aborts routing to the others.
 */
public interface NotificationChannel {

    /** Stable channel key matching a routing token (e.g. {@code "in_app"}, {@code "email"}). */
    String key();

    /** True when this channel is configured and can attempt delivery; false disables it (logged once). */
    boolean isEnabled();

    /** Deliver best-effort. Never throws — failures are handled internally. */
    void send(ResolvedNotification n);
}
