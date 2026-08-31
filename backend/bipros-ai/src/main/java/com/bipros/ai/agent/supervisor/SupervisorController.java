package com.bipros.ai.agent.supervisor;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.context.AiContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Supervisor-mode investigate endpoint. Streams the answer as SSE (clone of ChatController.chatStream)
 * so the frontend InvestigatePanel can render it turn-by-turn. Write-scoped because an investigation
 * can run agents (which write to ai.*).
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/agents")
@RequiredArgsConstructor
public class SupervisorController {

    private final SupervisorService supervisorService;
    private final AiContextResolver contextResolver;

    @PostMapping(value = "/investigate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@aiAccess.canWrite(#projectId)")
    public Flux<ServerSentEvent<String>> investigate(@PathVariable UUID projectId,
                                                     @RequestBody InvestigateRequest request) {
        AiContext ctx = contextResolver.resolve(projectId, "ai");
        return supervisorService.investigate(request.question(), ctx);
    }

    public record InvestigateRequest(String question) {
    }
}
