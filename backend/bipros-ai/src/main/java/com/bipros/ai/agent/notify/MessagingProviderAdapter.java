package com.bipros.ai.agent.notify;

/**
 * Transport for the WhatsApp / SMS channels. An implementation is driven by an
 * {@code AgentChannelConfig} row (provider URL, credentials, from-number). Kept behind an interface
 * so the concrete provider (Twilio-style today) can be swapped without touching the channels.
 */
public interface MessagingProviderAdapter {

    /** True when {@code channelKey} has an active, fully-populated provider config. */
    boolean isConfigured(String channelKey);

    /** Best-effort POST to the provider. Returns true on a 2xx response. NEVER throws. */
    boolean send(String channelKey, String toPhone, String message);
}
