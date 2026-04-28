package com.bipros.integration.adapter.sms;

/**
 * Sends SMS messages. The default implementation is a logging mock; a real
 * provider (Twilio, MSG91, Omantel) is wired up by replacing the bean in production.
 */
public interface SmsDispatcher {

    SmsDispatchResult send(SmsMessage message);

    record SmsMessage(String toMsisdn, String body, String purpose) {}

    record SmsDispatchResult(boolean success, String providerMessageId, String error) {
        public static SmsDispatchResult ok(String providerMessageId) {
            return new SmsDispatchResult(true, providerMessageId, null);
        }

        public static SmsDispatchResult error(String error) {
            return new SmsDispatchResult(false, null, error);
        }
    }
}
