package com.bipros.ai.agent.stream;

import com.bipros.ai.agent.core.AgentEventHub;
import com.bipros.ai.agent.core.AgentStreamEvent;
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
 * SSE-backed {@link AgentEventHub}. Fans {@link AgentStreamEvent}s out to browser subscribers over
 * Server-Sent Events. As a concrete {@code @Component} it replaces the {@code NoOpAgentEventHub}
 * (which is {@code @ConditionalOnMissingBean(AgentEventHub.class)}).
 *
 * <p>One {@link Sinks.Many} per project id, plus one keyed by a sentinel for the global/portfolio
 * stream (which sees every event). Emitting is best-effort and never throws — a lost event is
 * harmless. Each subscription is merged with a 25s heartbeat comment to keep the connection alive,
 * and its sink is dropped once the last subscriber disconnects.
 */
@Slf4j
@Component
public class SseAgentEventHub implements AgentEventHub {

    /** Sentinel key for the cross-project ("global") stream. */
    private static final UUID GLOBAL = new UUID(0L, 0L);
    private static final Duration HEARTBEAT = Duration.ofSeconds(25);
    private static final int BUFFER = 256;

    private final ObjectMapper objectMapper;
    private final Map<UUID, Sinks.Many<AgentStreamEvent>> sinks = new ConcurrentHashMap<>();

    public SseAgentEventHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void emit(AgentStreamEvent event) {
        try {
            UUID projectId = event.projectId();
            if (projectId != null) {
                Sinks.Many<AgentStreamEvent> projectSink = sinks.get(projectId);
                if (projectSink != null) {
                    projectSink.tryEmitNext(event);
                }
            }
            // The global stream sees every event (both project-scoped and portfolio/null-project).
            Sinks.Many<AgentStreamEvent> globalSink = sinks.get(GLOBAL);
            if (globalSink != null) {
                globalSink.tryEmitNext(event);
            }
        } catch (Exception ex) {
            // Best-effort — never let streaming affect a run.
            log.debug("SseAgentEventHub emit dropped: {}", ex.getMessage());
        }
    }

    /** SSE stream for a single project. */
    public Flux<ServerSentEvent<String>> streamForProject(UUID projectId) {
        return stream(projectId);
    }

    /** Cross-project ("global") SSE stream — sees every agent event. */
    public Flux<ServerSentEvent<String>> streamGlobal() {
        return stream(GLOBAL);
    }

    private Flux<ServerSentEvent<String>> stream(UUID key) {
        Sinks.Many<AgentStreamEvent> sink = sinks.computeIfAbsent(
                key, k -> Sinks.many().multicast().onBackpressureBuffer(BUFFER, false));

        Flux<ServerSentEvent<String>> events = sink.asFlux().map(this::toSse);
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(HEARTBEAT)
                .map(tick -> ServerSentEvent.<String>builder().comment("heartbeat").build());

        // Emit an immediate frame on subscribe so the client flips to live-stream mode at once,
        // instead of showing "polling" until the first agent event or the 25s heartbeat.
        ServerSentEvent<String> connected = ServerSentEvent.<String>builder()
                .event("connected").data("{\"ok\":true}").build();

        return Flux.concat(Flux.just(connected), Flux.merge(events, heartbeat))
                .doFinally(sig -> {
                    if (sink.currentSubscriberCount() == 0) {
                        sinks.remove(key, sink);
                    }
                });
    }

    private ServerSentEvent<String> toSse(AgentStreamEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            json = "{}";
        }
        return ServerSentEvent.<String>builder()
                .event(event.type())
                .data(json)
                .build();
    }
}
