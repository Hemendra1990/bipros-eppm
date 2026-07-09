package com.bipros.ai.agent.notify;

import com.bipros.common.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * SSE endpoint for the signed-in user's live notification feed. Auth-guarded — each subscriber only
 * ever receives their own notifications (the stream is keyed by the authenticated user id). Mirrors
 * the {@code text/event-stream} shape of {@code AgentStreamController}.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationSseHub hub;
    private final SecurityContextHelper securityContextHelper;

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Flux<ServerSentEvent<String>> stream() {
        UUID userId;
        try {
            userId = securityContextHelper.getCurrentUserId();
        } catch (RuntimeException noUser) {
            // Non-UUID principals (e.g. the legacy "admin" seed user) have no per-user stream.
            return Flux.empty();
        }
        return hub.stream(userId);
    }
}
