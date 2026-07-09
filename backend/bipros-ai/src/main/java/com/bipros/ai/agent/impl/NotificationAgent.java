package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.FindingStatus;
import com.bipros.ai.agent.notify.NotificationRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Notification agent (#11) — the final pipeline stage. Unlike the other agents it produces NO
 * findings: it reads the project's currently-notifiable ACTIVE findings (those a prior stage just
 * inserted or superseded, flagged {@code notifiable=true} by {@code AgentMemoryService}) and routes
 * each through {@link NotificationRouter} as a side effect, then returns an EMPTY {@link GatherResult}.
 *
 * <p>Because it emits no candidates, {@code AbstractAgent} never calls the LLM for it — it is
 * LLM-free by construction. Dedup lives in the router (delivery-audit + 24h in-app guard), so a
 * repeated run re-scans but re-sends nothing already delivered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAgent extends AbstractAgent {

    private static final String KEY = "notification";

    private final NotificationRouter router;
    private final AgentFindingRepository findingRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Notification";
    }

    @Override
    public boolean supportsPortfolio() {
        return true;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            // Portfolio/global runs have no single project's findings to route.
            return new GatherResult(snapshot, List.of());
        }

        List<AgentFinding> notifiable =
                findingRepository.findByProjectIdAndStatusAndNotifiableTrue(projectId, FindingStatus.ACTIVE);
        int routed = 0;
        for (AgentFinding f : notifiable) {
            try {
                router.route(f);
                routed++;
            } catch (Exception ex) {
                log.warn("NotificationAgent: routing failed for finding {}: {}", f.getId(), ex.getMessage());
            }
        }
        log.debug("NotificationAgent routed {}/{} notifiable findings for project {}",
                routed, notifiable.size(), projectId);

        // No candidates → no LLM narration, no findings persisted. Routing was the side effect.
        return new GatherResult(snapshot, List.of());
    }
}
