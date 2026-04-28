package com.bipros.integration.adapter.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Logs the SMS payload instead of dispatching. Active in every profile except {@code production}. */
@Component
@Profile("!production")
@Slf4j
public class MockSmsDispatcher implements SmsDispatcher {

    @Override
    public SmsDispatchResult send(SmsMessage message) {
        String pseudoId = "MOCK-" + UUID.randomUUID();
        log.info("[MockSms] purpose={} to={} body={} -> id={}",
                message.purpose(), message.toMsisdn(), message.body(), pseudoId);
        return SmsDispatchResult.ok(pseudoId);
    }
}
