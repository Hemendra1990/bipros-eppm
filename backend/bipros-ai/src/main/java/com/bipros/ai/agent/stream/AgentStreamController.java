package com.bipros.ai.agent.stream;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * SSE endpoints for the live "agents working" feed. Mirrors the SSE shape of {@code ChatController}
 * ({@code Flux<ServerSentEvent<String>>} over {@code text/event-stream}). Backed by
 * {@link SseAgentEventHub}.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AgentStreamController {

    private final SseAgentEventHub eventHub;

    /** Per-project stream. Read access to the project is required. */
    @GetMapping(value = "/projects/{projectId}/agents/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@aiAccess.canRead(#projectId)")
    public Flux<ServerSentEvent<String>> projectStream(@PathVariable UUID projectId) {
        return eventHub.streamForProject(projectId);
    }

    /** Cross-project stream. Same cross-project access semantics as the AI panel's general mode. */
    @GetMapping(value = "/agents/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@aiAccess.canRead(null)")
    public Flux<ServerSentEvent<String>> globalStream() {
        return eventHub.streamGlobal();
    }
}
