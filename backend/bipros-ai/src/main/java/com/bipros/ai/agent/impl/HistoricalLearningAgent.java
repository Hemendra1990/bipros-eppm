package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.FindingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Historical Learning agent. A synthesis agent that, for each significant issue currently active on
 * the project, looks across <em>other</em> projects for the same issue type already
 * {@link FindingStatus#RESOLVED_BY_USER resolved} — and surfaces how it was fixed as a proven
 * precedent.
 *
 * <p>Data source: current active HIGH/CRITICAL findings ({@link com.bipros.ai.agent.memory.AgentMemoryService})
 * matched against {@link AgentFindingRepository#findByFindingTypeAndStatusAndProjectIdNot} — resolved
 * instances of the same finding type on any other project. Finding:
 *
 * <ul>
 *   <li>{@code HISTORICAL_PRECEDENT} — current issues that have been solved before, with the proven
 *       corrective action from the past resolution.</li>
 * </ul>
 *
 * <p>Dormant on a fresh estate (no cross-project resolved history yet) — it accrues value as the
 * platform resolves findings across more projects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoricalLearningAgent extends AbstractAgent {

    private static final String KEY = "historical_learning";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_EXAMPLES = 6;

    private final AgentFindingRepository findingRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Historical Learning";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();
        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        // Significant issues currently open here (exclude this agent's own output).
        List<AgentFinding> current = runtime.memory().activeFindings(projectId, null, Severity.HIGH).stream()
                .filter(f -> !KEY.equals(f.getAgentKey()))
                .toList();

        Set<String> types = new LinkedHashSet<>();
        current.forEach(f -> types.add(f.getFindingType()));

        List<Precedent> precedents = new ArrayList<>();
        for (String type : types) {
            List<AgentFinding> resolved =
                    findingRepository.findByFindingTypeAndStatusAndProjectIdNot(
                            type, FindingStatus.RESOLVED_BY_USER, projectId);
            if (resolved.isEmpty()) continue;
            long projects = resolved.stream().map(AgentFinding::getProjectId).distinct().count();
            AgentFinding exemplar = resolved.stream()
                    .max(Comparator.comparingDouble(AgentFinding::getConfidence))
                    .orElse(resolved.get(0));
            precedents.add(new Precedent(type, resolved.size(), projects, exemplar));
        }

        snapshot.put("currentSignificantTypes", types.size());
        snapshot.put("typesWithPrecedent", precedents.size());
        if (precedents.isEmpty()) {
            return new GatherResult(snapshot, candidates); // dormant — no cross-project history yet
        }

        precedents.sort(Comparator.comparingInt((Precedent p) -> p.occurrences).reversed());
        candidates.add(precedentFinding(projectId, precedents, validUntil));
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft precedentFinding(UUID projectId, List<Precedent> precedents, Instant validUntil) {
        int n = precedents.size();
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Recurring issue types", String.valueOf(n)));
        for (Precedent p : precedents.subList(0, Math.min(MAX_EXAMPLES, n))) {
            String action = p.exemplar.getRecommendedAction() != null
                    ? shorten(p.exemplar.getRecommendedAction()) : "resolved previously";
            ev.add(EvidenceRef.metric(humanize(p.type),
                    "solved on " + p.projects + " past project" + (p.projects == 1 ? "" : "s") + " → " + action));
        }
        ev.add(EvidenceRef.entity("This project's board", "Open", "project", projectId,
                "/projects/" + projectId + "/ai"));

        Precedent lead = precedents.get(0);
        return new AgentFindingDraft(
                "HISTORICAL_PRECEDENT", "PROJECT", Severity.LOW, 0.8,
                "Current issues matched to the same finding type resolved on other projects",
                n + " current issue type" + (n == 1 ? "" : "s") + " have been solved before",
                n + " issue type" + (n == 1 ? "" : "s") + " active on this project have already been resolved on "
                        + "other projects — the leading one, " + humanize(lead.type) + ", was closed on "
                        + lead.projects + " past project" + (lead.projects == 1 ? "" : "s") + ".",
                "The estate has faced these same problems before and recorded how they were fixed — proven "
                        + "corrective actions rather than first-principles guessing.",
                "Reusing a corrective action that already worked elsewhere shortens recovery time and de-risks the "
                        + "fix versus solving each recurrence from scratch.",
                "Apply the proven action from the cited precedents (see evidence) and confirm the expected recovery "
                        + "on this project; if it works, resolve the matching finding to strengthen the precedent.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "PLANNING_ENGINEER", List.of()), validUntil);
    }

    private static String shorten(String s) {
        int dot = s.indexOf(". ");
        String out = dot > 0 ? s.substring(0, dot) : s;
        return out.length() > 110 ? out.substring(0, 107) + "…" : out;
    }

    private static String humanize(String findingType) {
        String[] parts = findingType.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return b.toString().trim();
    }

    private record Precedent(String type, int occurrences, long projects, AgentFinding exemplar) {
    }
}
