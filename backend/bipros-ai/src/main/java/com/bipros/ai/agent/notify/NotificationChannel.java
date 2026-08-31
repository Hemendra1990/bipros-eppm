package com.bipros.ai.agent.notify;

import com.bipros.ai.agent.domain.DeliveryStatus;

/**
 * One outbound delivery channel (in-app, email, whatsapp, sms). Implementations are discovered as
 * Spring beans and indexed by {@link #key()} in {@link NotificationRouter}.
 *
 * <p>Contract: {@link #send} MUST NOT throw. A transport failure is swallowed and logged, and the
 * returned {@link SendResult} tells the router what really happened so the delivery audit stays
 * honest (SENT vs PREVIEW vs SKIPPED vs FAILED) — one broken channel never aborts the others.
 */
public interface NotificationChannel {

    /** Stable channel key matching a routing token (e.g. {@code "in_app"}, {@code "email"}). */
    String key();

    /** True when this channel is configured and can attempt delivery; false disables it (logged once). */
    boolean isEnabled();

    /** Deliver best-effort. Never throws — returns the honest outcome for the audit row. */
    SendResult send(ResolvedNotification n);

    /** Outcome of one send: the status recorded on the delivery row plus an optional reason. */
    record SendResult(DeliveryStatus status, String detail) {
        public static SendResult sent() {
            return new SendResult(DeliveryStatus.SENT, null);
        }

        public static SendResult preview(String detail) {
            return new SendResult(DeliveryStatus.PREVIEW, detail);
        }

        public static SendResult skipped(String detail) {
            return new SendResult(DeliveryStatus.SKIPPED, detail);
        }

        public static SendResult failed(String detail) {
            return new SendResult(DeliveryStatus.FAILED, detail);
        }
    }
}
