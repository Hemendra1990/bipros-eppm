package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executive Insights agent (#10). Reads ONLY the shared agent memory — no heavy domain calls — and
 * synthesises a single board-ready brief from the project's active findings across all agents. The
 * one candidate it emits is where the LLM narrator does its most valuable work (turning the top
 * deterministic concerns into an executive narrative); confidence is the min of the cited findings.
 *
 * <p>Portfolio runs (projectId == null) are out of scope for this wave — a cross-project brief needs
 * the accessible-project list, deferred to a portfolio endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutiveInsightsAgent extends AbstractAgent {

    private static final String KEY = "executive_insights";
    private static final Duration TTL = Duration.ofHours(24);

    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Executive Insights";
    }

    @Override
    public boolean supportsPortfolio() {
        return true;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        if (ctx.projectId() == null) {
            // Portfolio synthesis deferred — needs the accessible-project roster.
            return new GatherResult(objectMapper.createObjectNode(), List.of());
        }

        List<AgentFinding> active = runtime.memory()
                .activeFindings(ctx.projectId(), null, Severity.MEDIUM)
                .stream()
                .filter(f -> !KEY.equals(f.getAgentKey()))   // never count or cite our own prior brief
                .sorted(Comparator
                        .comparingInt((AgentFinding f) -> f.getSeverity().ordinal()).reversed()
                        .thenComparing(Comparator.comparingDouble(AgentFinding::getConfidence).reversed()))
                .toList();

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("count", active.size());
        if (active.isEmpty()) {
            return new GatherResult(snapshot, List.of());
        }

        List<AgentFinding> top = active.subList(0, Math.min(3, active.size()));
        AgentFinding biggest = top.get(0);
        Set<String> agents = new LinkedHashSet<>();
        active.forEach(f -> agents.add(f.getAgentKey()));

        ArrayNode topArr = snapshot.putArray("top");
        StringBuilder concerns = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            AgentFinding f = top.get(i);
            ObjectNode row = topArr.addObject();
            row.put("type", f.getFindingType());
            row.put("severity", f.getSeverity().name());
            row.put("confidence", f.getConfidence());
            row.put("title", f.getTitle());
            concerns.append(i + 1).append(") ").append(f.getTitle())
                    .append(" (").append(f.getSeverity().name()).append("). ");
        }

        double confidence = top.stream().mapToDouble(AgentFinding::getConfidence).min().orElse(0.7);
        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);

        AgentFindingDraft brief = new AgentFindingDraft(
                "EXECUTIVE_BRIEF",
                "PROJECT",
                biggest.getSeverity(),
                confidence,
                "Min confidence of the " + top.size() + " cited findings (of " + active.size() + " active)",
                "Executive brief — " + active.size() + " active concern" + (active.size() == 1 ? "" : "s"),
                "Top concerns right now: " + concerns.toString().trim(),
                "Synthesised from " + active.size() + " active findings raised by " + agents.size()
                        + " agent" + (agents.size() == 1 ? "" : "s") + " (" + String.join(", ", agents) + ").",
                "The most material issue is \"" + biggest.getTitle() + "\" ("
                        + biggest.getSeverity().name() + ", " + humanize(biggest.getAgentKey())
                        + ") — it should anchor the next review.",
                "Address \"" + biggest.getTitle() + "\" first, then walk the top "
                        + top.size() + " concerns with the responsible owners.",
                buildEvidence(top),
                java.util.Map.of("PROJECT_MANAGER", List.of()),
                validUntil);

        return new GatherResult(snapshot, List.of(brief));
    }

    private List<EvidenceRef> buildEvidence(List<AgentFinding> top) {
        // Cited findings render as entity chips (label + title), not stat tiles — a title is prose, not a number.
        return top.stream()
                .map(f -> EvidenceRef.entity(
                        f.getSeverity().name() + " · " + humanize(f.getAgentKey()), f.getTitle(),
                        "finding", f.getId(), null))
                .toList();
    }

    private static String humanize(String agentKey) {
        if (agentKey == null) return "";
        String s = agentKey.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
