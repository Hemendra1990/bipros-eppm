package com.bipros.ai.agent.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user Server-Sent-Events fan-out for live in-app notifications. Mirrors the sink pattern of
 * {@code SseAgentEventHub} but keyed by recipient user id: one {@link Sinks.Many} per subscribed
 * user, a 25s heartbeat to keep the connection alive, and the sink dropped once the last subscriber
 * for that user disconnects. {@link #publish} is best-effort and never throws.
 */
@Slf4j
@Component
public class NotificationSseHub {

    private static final Duration HEARTBEAT = Duration.ofSeconds(25);
    private static final int BUFFER = 128;

    private final ObjectMapper objectMapper;
    private final Map<UUID, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    public NotificationSseHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Push a notification payload to {@code userId}'s live stream (no-op if they aren't subscribed). */
    public void publish(UUID userId, JsonNode payload) {
        if (userId == null || payload == null) {
            return;
        }
        Sinks.Many<String> sink = sinks.get(userId);
        if (sink == null) {
            return;
        }
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.debug("NotificationSseHub publish dropped for {}: {}", userId, ex.getMessage());
        }
    }

    /** SSE stream for one user; merges live notifications with a keep-alive heartbeat. */
    public Flux<ServerSentEvent<String>> stream(UUID userId) {
        Sinks.Many<String> sink = sinks.computeIfAbsent(
                userId, k -> Sinks.many().multicast().onBackpressureBuffer(BUFFER, false));

        Flux<ServerSentEvent<String>> events = sink.asFlux()
                .map(json -> ServerSentEvent.<String>builder().event("notification").data(json).build());
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(HEARTBEAT)
                .map(tick -> ServerSentEvent.<String>builder().comment("heartbeat").build());

        return Flux.merge(events, heartbeat)
                .doFinally(sig -> {
                    if (sink.currentSubscriberCount() == 0) {
                        sinks.remove(userId, sink);
                    }
                });
    }
}
